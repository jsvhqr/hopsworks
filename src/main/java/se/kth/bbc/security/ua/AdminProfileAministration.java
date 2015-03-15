/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package se.kth.bbc.security.ua;

import java.io.IOException;
import java.io.Serializable;
import java.security.Principal;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.PostConstruct;
import javax.ejb.EJB;
import javax.ejb.EJBException;
import javax.faces.bean.ManagedBean;
import javax.faces.bean.ViewScoped;
import javax.faces.context.ExternalContext;
import javax.faces.context.FacesContext;
import javax.servlet.http.HttpServletRequest;
import se.kth.bbc.lims.MessagesController;
import se.kth.bbc.security.ua.model.User;
import se.kth.bbc.security.ua.model.Userlogins;

/**
 *
 * @author Ali Gholami <gholami@pdc.kth.se>
 */
@ManagedBean
@ViewScoped
public class AdminProfileAministration implements Serializable {

    private static final long serialVersionUID = 1L;

    @EJB
    private UserManager userManager;

    private User user;
    // for modifying user roles and status
    private User editingUser;

    // to remove an existing group
    private String selectedGroup;

    // to assign a new stauts
    private String selectedStatus;

    // to assign a new group
    private String newGroup;

    // all groups
    List<String> groups;

    // all existing groups belong tp
    List<String> currentGroups;

    // all possible new groups user doesnt belong to
    List<String> newGroups;

    // current status of the editing user
    private String editStatus;

    List<String> status;

    private Userlogins login;

    public Userlogins getLogin() {
        return login;
    }

    public void setLogin(Userlogins login) {
        this.login = login;
    }

    public void setEditStatus(String editStatus) {
        this.editStatus = editStatus;
    }

    public String getNew_group() {
        return newGroup;
    }

    public void setNew_group(String new_group) {
        this.newGroup = new_group;
    }

    public User getEditingUser() {
        return editingUser;
    }

    public void setEditingUser(User editingUser) {
        this.editingUser = editingUser;
    }

    public List<String> getUserRole(User p) {
        List<String> list = userManager.findGroups(p.getUid());
        return list;
    }

    public String getChangedStatus(User p) {
        return PeoplAccountStatus.values()[userManager.findByEmail(p.getEmail()).getStatus() - 1].name();
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public void setNewGroups(List<String> newGroups) {
        this.newGroups = newGroups;
    }

    public String getSelectedStatus() {
        return selectedStatus;
    }

    public void setSelectedStatus(String selectedStatus) {
        this.selectedStatus = selectedStatus;
    }

    public String getSelectedGroup() {
        return selectedGroup;
    }

    public void setSelectedGroup(String selectedGroup) {
        this.selectedGroup = selectedGroup;
    }

    /**
     * Filter the current groups of the user.
     *
     * @return
     */
    public List<String> getCurrentGroups() {
        List<String> list = userManager.findGroups(editingUser.getUid());
        return list;
    }

    public void setCurrentGroups(List<String> currentGroups) {
        this.currentGroups = currentGroups;
    }

    public List<String> getNewGroups() {
        List<String> list = userManager.findGroups(editingUser.getUid());
        List<String> tmp = new ArrayList<>();

        for (BBCGroups b : BBCGroups.values()) {

            if (!list.contains(b.name())) {
                tmp.add(b.name());
            }
        }
        return tmp;
    }

    public String getEditStatus() {

        int status = userManager.getUser(this.editingUser.getEmail()).getStatus();
        this.editStatus = PeoplAccountStatus.values()[status - 1].name();
        return this.editStatus;
    }

    @PostConstruct
    public void init() {

        groups = new ArrayList<>();
        status = new ArrayList<>();

        for (BBCGroups value : BBCGroups.values()) {
            groups.add(value.name());
        }

        editingUser = (User) FacesContext.getCurrentInstance().getExternalContext()
                .getSessionMap().get("editinguser");

        login = userManager.getLastUserLoing(editingUser.getUid());

    }

    public List<String> getStatus() {

        status = new ArrayList<>();

        for (PeoplAccountStatus p : PeoplAccountStatus.values()) {
            status.add(p.name());
        }

        // Remove the inactive users
        status.remove(PeoplAccountStatus.MOBILE_ACCOUNT_INACTIVE.name());
        status.remove(PeoplAccountStatus.YUBIKEY_ACCOUNT_INACTIVE.name());

        return status;
    }

    public void setStatus(List<String> status) {
        this.status = status;
    }

    public List<User> getUsersNameList() {
        return userManager.findAllUsers();
    }

    public List<String> getGroups() {
        return groups;
    }

    public User getSelectedUser() {
        return user;
    }

    public void setSelectedUser(User user) {
        this.user = user;
    }

    public String getLoginName() throws IOException {
        FacesContext context = FacesContext.getCurrentInstance();
        HttpServletRequest request = (HttpServletRequest) context.getExternalContext().getRequest();

        Principal principal = request.getUserPrincipal();

        try {
            User p = userManager.findByEmail(principal.getName());
            if (p != null) {
                return p.getName();
            } else {
                return principal.getName();
            }
        } catch (Exception ex) {
            ExternalContext extContext = FacesContext.getCurrentInstance().getExternalContext();
            extContext.redirect(extContext.getRequestContextPath());
            return null;
        }
    }

    /**
     * Update user roles from profile by admin.
     */
    public void updateStatusByAdmin() {
        // Update status
        if (!"#".equals(selectedStatus)) {
            editingUser.setStatus(PeoplAccountStatus.valueOf(selectedStatus).getValue());
            userManager.updateStatus(editingUser.getUid(), PeoplAccountStatus.valueOf(selectedStatus).getValue());
            MessagesController.addInfoMessage("Success", "Status updated successfully.");

        } else {
            MessagesController.addErrorMessage("Error", "Faied to update!");

        }

    }

    public void addRoleByAdmin() {

        // Register a new group
        if (!"#".equals(newGroup)) {
            userManager.registerGroup(editingUser.getUid(), BBCGroups.valueOf(newGroup).getValue());
            MessagesController.addInfoMessage("Success", "Role updated successfully.");
            
        } else {
            MessagesController.addErrorMessage("Error", "Faied to update!");

        }

    }

    public void removeRoleByAdmin() {

        // Remove a group
        if (!"#".equals(selectedGroup)) {
            if (selectedGroup.equals(BBCGroups.BBC_GUEST.name())) {
                MessagesController.addMessageToGrowl(BBCGroups.BBC_GUEST.name() + " can not be removed.");
            } else {
                userManager.removeGroup(editingUser.getUid(), BBCGroups.valueOf(selectedGroup).getValue());
                MessagesController.addMessageToGrowl("User updated successfully.");
            }
        }

        if ("#".equals(selectedGroup)) {

            if (("#".equals(selectedStatus))
                    || "#".equals(newGroup)) {
                MessagesController.addErrorMessage("Error", "No selection made!");
            }
        }

    }
}
