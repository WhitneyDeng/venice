package com.linkedin.venice.controllerapi.request;

public class KillOfflinePushJobRequest extends ControllerRequest {
  private String topicName;

  public KillOfflinePushJobRequest() {
  }

  public KillOfflinePushJobRequest(String clusterName, String topicName) {
    super(clusterName);
    this.topicName = topicName;
  }

  public void setTopicName(String topicName) {
    this.topicName = topicName;
  }

  public String getTopicName() {
    return topicName;
  }
}
