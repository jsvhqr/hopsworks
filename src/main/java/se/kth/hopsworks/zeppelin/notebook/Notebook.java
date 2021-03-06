/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package se.kth.hopsworks.zeppelin.notebook;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.conf.ZeppelinConfiguration.ConfVars;
import org.apache.zeppelin.display.AngularObject;
import org.apache.zeppelin.interpreter.InterpreterFactory;
import org.apache.zeppelin.interpreter.InterpreterSetting;
import org.apache.zeppelin.notebook.JobListenerFactory;
import org.apache.zeppelin.notebook.Note;
import org.apache.zeppelin.notebook.NoteInfo;
import org.apache.zeppelin.notebook.NoteInterpreterLoader;
import org.apache.zeppelin.notebook.Paragraph;
import org.apache.zeppelin.notebook.repo.NotebookRepo;
import org.apache.zeppelin.scheduler.SchedulerFactory;
import org.quartz.CronScheduleBuilder;
import org.quartz.CronTrigger;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.JobKey;
import org.quartz.SchedulerException;
import org.quartz.TriggerBuilder;
import org.quartz.impl.StdSchedulerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.kth.hopsworks.zeppelin.server.ZeppelinSingleton;

/**
 * Collection of Notes.
 */
public class Notebook {

  Logger logger = LoggerFactory.getLogger(Notebook.class);
  private final ZeppelinSingleton zeppelin = ZeppelinSingleton.SINGLETON;
  private final SchedulerFactory schedulerFactory = zeppelin.
          getSchedulerFactory();
  private final InterpreterFactory replFactory = zeppelin.
          getReplFactory();
  /**
   * Keep the order.
   */
  private static final Map<String, Note> notes = new LinkedHashMap<>();//all notes
  private final Map<String, Note> notesInProject = new LinkedHashMap<>();//notes in project
  private final ZeppelinConfiguration conf = zeppelin.getConf();
  private static StdSchedulerFactory quertzSchedFact;
  private static org.quartz.Scheduler quartzSched;
  private JobListenerFactory jobListenerFactory = zeppelin.
          getNotebookServer();
  private NotebookRepo notebookRepo;

  public Notebook() {
  }

  public Notebook(NotebookRepo notebookRepo) throws IOException,
          SchedulerException {

    this.notebookRepo = notebookRepo;
    quertzSchedFact = new org.quartz.impl.StdSchedulerFactory();
    quartzSched = quertzSchedFact.getScheduler();
    quartzSched.start();
    CronJob.notebook = this;

    loadAllNotes();
  }

  /**
   * Create new note.
   *
   * @return
   * @throws IOException
   */
  public Note createNote() throws IOException {
    if (conf.getBoolean(ConfVars.ZEPPELIN_NOTEBOOK_AUTO_INTERPRETER_BINDING)) {
      return createNote(replFactory.getDefaultInterpreterSettingList());
    } else {
      return createNote(null);
    }
  }

  /**
   * Create new note.
   *
   * @param interpreterIds
   * @return
   * @throws IOException
   */
  public Note createNote(List<String> interpreterIds) throws IOException {
    NoteInterpreterLoader intpLoader = new NoteInterpreterLoader(replFactory);
    Note note = new Note(notebookRepo, intpLoader, jobListenerFactory);
    intpLoader.setNoteId(note.id());
    notesInProject.put(note.id(), note);
    synchronized (notes) {
      notes.put(note.id(), note);
    }
    if (interpreterIds != null) {
      bindInterpretersToNote(note.id(), interpreterIds);
    }

    note.persist();
    return note;
  }

  public void bindInterpretersToNote(String id,
          List<String> interpreterSettingIds) throws IOException {
    Note note = getNote(id);
    if (note != null) {
      note.getNoteReplLoader().setInterpreters(interpreterSettingIds);
      replFactory.putNoteInterpreterSettingBinding(id, interpreterSettingIds);
    }
  }

  public List<String> getBindedInterpreterSettingsIds(String id) {
    Note note = getNote(id);
    if (note != null) {
      return note.getNoteReplLoader().getInterpreters();
    } else {
      return new LinkedList<>();
    }
  }

  public List<InterpreterSetting> getBindedInterpreterSettings(String id) {
    Note note = getNote(id);
    if (note != null) {
      return note.getNoteReplLoader().getInterpreterSettings();
    } else {
      return new LinkedList<>();
    }
  }

  private Note getNote(String id) {
    synchronized (notes) {
      return notes.get(id);
    }
  }

  public Note getNoteInProject(String id) {
    return notesInProject.get(id);
  }

  public void removeNote(String id) {
    Note note;
    notesInProject.remove(id);
    synchronized (notes) {
      note = notes.remove(id);
    }
    try {
      note.unpersist();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  private Note loadNoteFromRepo(String id) {
    Note note = null;
    try {
      note = notebookRepo.get(id);
    } catch (IOException e) {
      logger.error("Failed to load " + id, e);
    }
    if (note == null) {
      return null;
    }

    // set NoteInterpreterLoader
    NoteInterpreterLoader noteInterpreterLoader = new NoteInterpreterLoader(
            replFactory);
    note.setReplLoader(noteInterpreterLoader);
    noteInterpreterLoader.setNoteId(note.id());

    // set JobListenerFactory
    note.setJobListenerFactory(jobListenerFactory);

    // set notebookRepo
    note.setNotebookRepo(notebookRepo);

    Map<String, SnapshotAngularObject> angularObjectSnapshot = new HashMap<>();

    // restore angular object --------------
    Date lastUpdatedDate = new Date(0);
    for (Paragraph p : note.getParagraphs()) {
      p.setNote(note);
      if (p.getDateFinished() != null && lastUpdatedDate.before(p.
              getDateFinished())) {
        lastUpdatedDate = p.getDateFinished();
      }
    }

    Map<String, List<AngularObject>> savedObjects = note.getAngularObjects();

    if (savedObjects != null) {
      for (String intpGroupName : savedObjects.keySet()) {
        List<AngularObject> objectList = savedObjects.get(intpGroupName);

        for (AngularObject savedObject : objectList) {
          SnapshotAngularObject snapshot = angularObjectSnapshot.get(
                  savedObject.getName());
          if (snapshot == null || snapshot.getLastUpdate().before(
                  lastUpdatedDate)) {
            angularObjectSnapshot.put(
                    savedObject.getName(),
                    new SnapshotAngularObject(
                            intpGroupName,
                            savedObject,
                            lastUpdatedDate));
          }
        }
      }
    }
    notesInProject.put(note.id(), note);
    synchronized (notes) {
      notes.put(note.id(), note);
      refreshCron(note.id());
    }
    return note;
  }

  private void loadAllNotes() throws IOException {
    List<NoteInfo> noteInfos = notebookRepo.list();

    for (NoteInfo info : noteInfos) {
      loadNoteFromRepo(info.getId());
    }
  }

  class SnapshotAngularObject {

    String intpGroupId;
    AngularObject angularObject;
    Date lastUpdate;

    public SnapshotAngularObject(String intpGroupId,
            AngularObject angularObject, Date lastUpdate) {
      super();
      this.intpGroupId = intpGroupId;
      this.angularObject = angularObject;
      this.lastUpdate = lastUpdate;
    }

    public String getIntpGroupId() {
      return intpGroupId;
    }

    public AngularObject getAngularObject() {
      return angularObject;
    }

    public Date getLastUpdate() {
      return lastUpdate;
    }
  }

  public List<Note> getAllNotes() {

    List<Note> noteList = new ArrayList<>(notesInProject.values());
    Collections.sort(noteList, new Comparator() {
      @Override
      public int compare(Object one, Object two) {
        Note note1 = (Note) one;
        Note note2 = (Note) two;

        String name1 = note1.id();
        if (note1.getName() != null) {
          name1 = note1.getName();
        }
        String name2 = note2.id();
        if (note2.getName() != null) {
          name2 = note2.getName();
        }
        ((Note) one).getName();
        return name1.compareTo(name2);
      }
    });
    return noteList;
  }

  public JobListenerFactory getJobListenerFactory() {
    return jobListenerFactory;
  }

  public void setJobListenerFactory(JobListenerFactory jobListenerFactory) {
    this.jobListenerFactory = jobListenerFactory;
  }

  /**
   * Cron task for the note.
   *
   * @author Leemoonsoo
   *
   */
  public static class CronJob implements org.quartz.Job {

    public static Notebook notebook;

    @Override
    public void execute(JobExecutionContext context) throws
            JobExecutionException {

      String noteId = context.getJobDetail().getJobDataMap().getString("noteId");
      Note note = notebook.getNote(noteId);
      note.runAll();
    }
  }

  public void refreshCron(String id) {
    removeCron(id);
    synchronized (notes) {

      Note note = notes.get(id);
      if (note == null) {
        return;
      }
      Map<String, Object> config = note.getConfig();
      if (config == null) {
        return;
      }

      String cronExpr = (String) note.getConfig().get("cron");
      if (cronExpr == null || cronExpr.trim().length() == 0) {
        return;
      }

      JobDetail newJob = JobBuilder.newJob(CronJob.class).withIdentity(id,
              "note").usingJobData("noteId", id)
              .build();

      Map<String, Object> info = note.getInfo();
      info.put("cron", null);

      CronTrigger trigger = null;
      try {
        trigger = TriggerBuilder.newTrigger().withIdentity("trigger_" + id,
                "note")
                .withSchedule(CronScheduleBuilder.cronSchedule(cronExpr)).
                forJob(id, "note")
                .build();
      } catch (Exception e) {
        logger.error("Error", e);
        info.put("cron", e.getMessage());
      }

      try {
        if (trigger != null) {
          quartzSched.scheduleJob(newJob, trigger);
        }
      } catch (SchedulerException e) {
        logger.error("Error", e);
        info.put("cron", "Scheduler Exception");
      }
    }
  }

  private void removeCron(String id) {
    try {
      quartzSched.deleteJob(new JobKey(id, "note"));
    } catch (SchedulerException e) {
      logger.error("Can't remove quertz " + id, e);
    }
  }

  public InterpreterFactory getInterpreterFactory() {
    return replFactory;
  }

  public void setNotebookRepo(NotebookRepo notebookRepo) {
    this.notebookRepo = notebookRepo;
  }
}