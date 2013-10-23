/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package se.kth.kthfsdashboard.virtualization.clusterparser;

import java.io.Serializable;
import java.util.List;

/**
 *
 * @author Alberto Lorente Leal <albll@kth.se>
 */

public class NodeGroup implements Serializable{

    private String service;
    private int number;
    private List<String> recipes;
    private List<Integer> authorizePorts;
    private String chefAttributes;
    private String bittorrent;

    public NodeGroup(){
        
    }
    
    public NodeGroup(String name){
        this.service=name;
        
    }
       
    public String getService() {
        return service;
    }

    public void setService(String name) {
        this.service = name;
    }

    public int getNumber() {
        return number;
    }

    public void setNumber(int number) {
        this.number = number;
    }

    public List<String> getRecipes() {
        return recipes;
    }

    public void setRecipes(List<String> recipes) {
        this.recipes = recipes;
    }

    public List<Integer> getAuthorizePorts() {
        return authorizePorts;
    }

    public void setAuthorizePorts(List<Integer> authorizePorts) {
        this.authorizePorts = authorizePorts;
    }

    public String getChefAttributes() {
        return chefAttributes;
    }

    public void setChefAttributes(String chefAttributes) {
        this.chefAttributes = chefAttributes;
    }

    public String getBittorrent() {
        return bittorrent;
    }

    public void setBittorrent(String bittorrent) {
        this.bittorrent = bittorrent;
    }

    @Override
    public String toString() {
        return "NodeGroup{" + "securityGroup=" + service + ", number=" + number + ", roles=" + recipes + ", authorizePorts=" + authorizePorts + '}';
    }
         
    
}
