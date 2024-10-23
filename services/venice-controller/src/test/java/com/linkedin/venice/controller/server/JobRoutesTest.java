package com.linkedin.venice.controller.server;

import static com.linkedin.venice.controllerapi.ControllerApiConstants.*;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.linkedin.venice.controller.Admin;
import com.linkedin.venice.controller.VeniceControllerRequestHandler;
import com.linkedin.venice.controller.VeniceControllerRequestHandlerDependencies;
import com.linkedin.venice.controller.VeniceParentHelixAdmin;
import com.linkedin.venice.controllerapi.JobStatusQueryResponse;
import com.linkedin.venice.meta.Version;
import com.linkedin.venice.pushmonitor.ExecutionStatus;
import com.linkedin.venice.utils.ObjectMapperFactory;
import com.linkedin.venice.utils.Utils;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import spark.Request;
import spark.Response;
import spark.Route;


public class JobRoutesTest {
  private static final Logger LOGGER = LogManager.getLogger(JobRoutesTest.class);

  private VeniceControllerRequestHandler requestHandler;
  private Admin mockAdmin;

  @BeforeMethod
  public void setUp() {
    mockAdmin = mock(VeniceParentHelixAdmin.class);
    VeniceControllerRequestHandlerDependencies dependencies = mock(VeniceControllerRequestHandlerDependencies.class);
    doReturn(mockAdmin).when(dependencies).getAdmin();
    requestHandler = new VeniceControllerRequestHandler(dependencies);
  }

  @Test
  public void testPopulateJobStatus() throws Exception {
    doReturn(true).when(mockAdmin).isLeaderControllerFor(anyString());
    doReturn(new Admin.OfflinePushStatusInfo(ExecutionStatus.COMPLETED)).when(mockAdmin)
        .getOffLinePushStatus(anyString(), anyString(), any(), any(), any());

    doReturn(2).when(mockAdmin).getReplicationFactor(anyString(), anyString());

    String cluster = Utils.getUniqueString("cluster");
    String store = Utils.getUniqueString("store");
    int version = 5;
    String incPushVersion = "IncPus_update_xyz";
    String kafkaTopicName = Version.composeKafkaTopic(store, version);
    String targetedRegion = "";
    Admin.OfflinePushStatusInfo offlinePushStatusInfo = new Admin.OfflinePushStatusInfo(
        ExecutionStatus.COMPLETED,
        System.currentTimeMillis(),
        Collections.emptyMap(),
        "XYZ",
        Collections.emptyMap(),
        Collections.emptyMap());

    when(
        mockAdmin.getOffLinePushStatus(
            cluster,
            kafkaTopicName,
            Optional.ofNullable(incPushVersion),
            "region-1",
            targetedRegion)).thenReturn(offlinePushStatusInfo);

    Request sparkRequestMock = mock(Request.class);
    when(sparkRequestMock.queryParams(CLUSTER)).thenReturn(cluster);
    when(sparkRequestMock.queryParams(NAME)).thenReturn(store);
    when(sparkRequestMock.queryParams(VERSION)).thenReturn("" + version);
    when(sparkRequestMock.queryParams(INCREMENTAL_PUSH_VERSION)).thenReturn(incPushVersion);
    when(sparkRequestMock.queryParams(TARGETED_REGIONS)).thenReturn(targetedRegion);

    Route jobRoutes = new JobRoutes(false, Optional.empty(), requestHandler).jobStatus(mockAdmin);
    JobStatusQueryResponse actualResponse = ObjectMapperFactory.getInstance()
        .readValue(jobRoutes.handle(sparkRequestMock, mock(Response.class)).toString(), JobStatusQueryResponse.class);

    Map<String, String> extraInfo = actualResponse.getExtraInfo();
    LOGGER.info("extraInfo: {}", extraInfo);
    Assert.assertNotNull(extraInfo);

    Map<String, String> extraDetails = actualResponse.getExtraDetails();
    LOGGER.info("extraDetails: {}", extraDetails);
    Assert.assertNotNull(extraDetails);
  }
}
