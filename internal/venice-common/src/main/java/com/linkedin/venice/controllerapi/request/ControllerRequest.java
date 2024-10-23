package com.linkedin.venice.controllerapi.request;

/**
 * Extend this class to create request objects for the controller
 */
public class ControllerRequest {
  private String clusterName;

  // TODO: make clusterName final again, when figure out how to mock "cluster" for JobStatusRequest in JobRoutesTest
  public ControllerRequest() {
  }

  public ControllerRequest(String clusterName) {
    this.clusterName = clusterName;
  }

  public void setClusterName(String clusterName) {
    this.clusterName = clusterName;
  }

  public String getClusterName() {
    return clusterName;
  }
}
