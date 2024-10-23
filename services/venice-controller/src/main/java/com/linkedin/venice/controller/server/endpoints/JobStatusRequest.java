package com.linkedin.venice.controller.server.endpoints;

import static com.linkedin.venice.controllerapi.ControllerApiConstants.*;

import com.linkedin.venice.controllerapi.request.ControllerRequest;
import com.linkedin.venice.utils.Utils;


public class JobStatusRequest extends ControllerRequest {
  // TODO: extend ControllerRequest (refer to NewStoreRequest)
  private String storeName;
  private int versionNumber;
  private String incrementalPushVersion;
  private String targetedRegions;
  private String region;

  public JobStatusRequest() {
  }

  public JobStatusRequest(
      String clusterName,
      String storeName,
      int versionNumber,
      String incrementalPushVersion,
      String targetedRegions,
      String region) {
    super(clusterName);
    this.storeName = storeName;
    this.versionNumber = versionNumber;
    this.incrementalPushVersion = incrementalPushVersion;
    this.targetedRegions = targetedRegions;
    this.region = region;
  }

  public void setStoreName(String store) {
    this.storeName = store;
  }

  public String getStoreName() {
    return storeName;
  }

  public void setVersionNumber(String versionNumber) {
    this.versionNumber = parseVersionNumber(versionNumber);
  }

  public void setVersionNumber(int versionNumber) {
    this.versionNumber = versionNumber;
  }

  private int parseVersionNumber(String versionNumber) {
    return Utils.parseIntFromString(versionNumber, VERSION);
  }

  public int getVersionNumber() {
    return versionNumber;
  }

  public void setIncrementalPushVersion(String incrementalPushVersion) {
    this.incrementalPushVersion = incrementalPushVersion;
  }

  public String getIncrementalPushVersion() {
    return incrementalPushVersion;
  }

  public void setTargetedRegions(String targetedRegions) {
    this.targetedRegions = targetedRegions;
  }

  public String getTargetedRegions() {
    return targetedRegions;
  }

  public void setRegion(String region) {
    this.region = region;
  }

  public String getRegion() {
    return region;
  }
}
