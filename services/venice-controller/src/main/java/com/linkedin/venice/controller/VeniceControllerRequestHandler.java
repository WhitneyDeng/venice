package com.linkedin.venice.controller;

import com.linkedin.venice.controllerapi.ControllerResponse;
import com.linkedin.venice.controllerapi.JobStatusQueryResponse;
import com.linkedin.venice.controllerapi.LeaderControllerResponse;
import com.linkedin.venice.controllerapi.request.JobStatusRequest;
import com.linkedin.venice.controllerapi.request.KillOfflinePushJobRequest;
import com.linkedin.venice.controllerapi.request.NewStoreRequest;
import com.linkedin.venice.meta.Instance;
import com.linkedin.venice.meta.Version;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;


/**
 * The core handler for processing incoming requests in the VeniceController.
 * Acts as the central entry point for handling requests received via both HTTP/REST and gRPC protocols.
 * This class is responsible for managing all request handling operations for the VeniceController.
 */
public class VeniceControllerRequestHandler {
  private static final Logger LOGGER = LogManager.getLogger(VeniceControllerGrpcServiceImpl.class);
  private final Admin admin;
  private final boolean sslEnabled;

  public VeniceControllerRequestHandler(VeniceControllerRequestHandlerDependencies dependencies) {
    this.admin = dependencies.getAdmin();
    this.sslEnabled = dependencies.isSslEnabled();
  }

  /**
   * Creates a new store in the specified Venice cluster with the provided parameters.
   * @param request the request object containing all necessary details for the creation of the store
   */
  public void createStore(NewStoreRequest request) {
    LOGGER.info(
        "Creating store: {} in cluster: {} with owner: {} and key schema: {} and value schema: {} and isSystemStore: {} and access permissions: {}",
        request.getStoreName(),
        request.getClusterName(),
        request.getOwner(),
        request.getKeySchema(),
        request.getValueSchema(),
        request.isSystemStore(),
        request.getAccessPermissions());
    admin.createStore(
        request.getClusterName(),
        request.getStoreName(),
        request.getOwner(),
        request.getKeySchema(),
        request.getValueSchema(),
        request.isSystemStore(),
        Optional.ofNullable(request.getAccessPermissions()));
    LOGGER.info("Store: {} created successfully in cluster: {}", request.getStoreName(), request.getClusterName());
  }

  public void queryJobStatus(JobStatusRequest jobStatusRequest, JobStatusQueryResponse response) {
    String store = jobStatusRequest.getStoreName();
    int versionNumber = jobStatusRequest.getVersionNumber();
    String cluster = jobStatusRequest.getClusterName();
    String incrementalPushVersion = jobStatusRequest.getIncrementalPushVersion();
    String region = jobStatusRequest.getRegion();
    String targetedRegions = jobStatusRequest.getTargetedRegions();

    String kafkaTopicName = Version.composeKafkaTopic(store, versionNumber);
    Admin.OfflinePushStatusInfo offlineJobStatus = admin.getOffLinePushStatus(
        cluster,
        kafkaTopicName,
        Optional.ofNullable(incrementalPushVersion),
        region,
        targetedRegions);
    response.setVersion(versionNumber);
    response.setStatus(offlineJobStatus.getExecutionStatus().toString());
    response.setStatusDetails(offlineJobStatus.getStatusDetails());
    response.setStatusUpdateTimestamp(offlineJobStatus.getStatusUpdateTimestamp());
    response.setExtraInfo(offlineJobStatus.getExtraInfo());
    response.setExtraDetails(offlineJobStatus.getExtraDetails());
    response.setExtraInfoUpdateTimestamp(offlineJobStatus.getExtraInfoUpdateTimestamp());

    response.setCluster(cluster);
    response.setName(store);
  }

  public void killOfflinePushJob(KillOfflinePushJobRequest request, ControllerResponse response) {
    String cluster = request.getClusterName();
    String topic = request.getTopicName();

    LOGGER.info("Killing Push Job: {} in cluster: {}", topic, cluster);
    admin.killOfflinePush(cluster, topic, false);
    LOGGER.info("Push Job: {} killed successfully in cluster: {}", topic, cluster);

    response.setCluster(cluster);
    response.setName(Version.parseStoreFromKafkaTopicName(topic));
  }

  public void getLeaderController(String clusterName, LeaderControllerResponse response) {
    Instance leaderController = admin.getLeaderController(clusterName);
    response.setCluster(clusterName);
    response.setUrl(leaderController.getUrl(isSslEnabled()));
    if (leaderController.getPort() != leaderController.getSslPort()) {
      // Controller is SSL Enabled
      response.setSecureUrl(leaderController.getUrl(true));
    }
    response.setGrpcUrl(leaderController.getGrpcUrl());
    response.setSecureGrpcUrl(leaderController.getGrpcSslUrl());
  }

  // visibility: package-private
  boolean isSslEnabled() {
    return sslEnabled;
  }
}
