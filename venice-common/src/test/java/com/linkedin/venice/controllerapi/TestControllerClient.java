package com.linkedin.venice.controllerapi;

import com.linkedin.d2.balancer.D2Client;
import com.linkedin.d2.balancer.D2ClientBuilder;
import com.linkedin.venice.D2.D2ClientUtils;
import com.linkedin.venice.integration.utils.MockD2ServerWrapper;
import com.linkedin.venice.integration.utils.MockHttpServerWrapper;
import com.linkedin.venice.integration.utils.ServiceFactory;
import com.linkedin.venice.utils.TestUtils;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import java.util.Optional;

import org.codehaus.jackson.map.ObjectMapper;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.IOException;

public class TestControllerClient {
  @Test
  public static void clientReturnsErrorObjectOnConnectionFailure(){
    ControllerClient client = new ControllerClient(TestUtils.getUniqueString("cluster"), "http://localhost:17079");
    StoreResponse r3 = client.getStore("mystore");
    Assert.assertTrue(r3.isError());
  }

  private static class TestJsonObject {
    private String field1;
    private String field2;

    public String getField1() {
      return field1;
    }
    public String getField2() {
      return field2;
    }
    public void setField1(String fld) {
      field1 = fld;
    }
    public void setField2(String fld) {
      field2 = fld;
    }
  }
  @Test
  public void testObjectMapperIgnoringUnknownProperties() throws IOException {
    ObjectMapper objectMapper = ControllerTransport.getObjectMapper();
    String field1Value = "field1_value";
    String jsonStr = "{\"field1\":\"" + field1Value + "\",\"field3\":\"" + field1Value + "\"}";
    TestJsonObject jsonObject = objectMapper.readValue(jsonStr, TestJsonObject.class);
    Assert.assertEquals(jsonObject.getField1(), field1Value);
    Assert.assertNull(jsonObject.getField2());
  }

  @Test
  public void testGetMasterControllerUrlWithUrlContainingSpace() throws IOException {
    String clusterName = TestUtils.getUniqueString("test-cluster");
    String fakeMasterControllerUrl = "http://fake.master.controller.url";
    try (MockHttpServerWrapper mockController = ServiceFactory.getMockHttpServer("mock_controller")) {
      MasterControllerResponse controllerResponse = new MasterControllerResponse();
      controllerResponse.setCluster(clusterName);
      controllerResponse.setUrl(fakeMasterControllerUrl);
      ObjectMapper objectMapper = new ObjectMapper();
      ByteBuf body = Unpooled.wrappedBuffer(objectMapper.writeValueAsBytes(controllerResponse));
      FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, body);
      response.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/json");
      // We must specify content_length header, otherwise netty will keep polling, since it
      // doesn't know when to finish writing the response.
      response.headers().set(HttpHeaderNames.CONTENT_LENGTH, response.content().readableBytes());
      mockController.addResponseForUriPattern(ControllerRoute.MASTER_CONTROLLER.getPath() + ".*", response);

      String controllerUrlWithSpaceAtBeginning = "   http://" + mockController.getAddress();
      ControllerClient controllerClient = new ControllerClient(clusterName, controllerUrlWithSpaceAtBeginning);
      String masterControllerUrl = controllerClient.getMasterControllerUrl();
      Assert.assertEquals(masterControllerUrl, fakeMasterControllerUrl);
    }
  }

  @Test
  public void testD2ControllerClient() throws Exception{
    String d2ClusterName = "VeniceRouter";
    String d2ServiceName = "VeniceRouter";
    String veniceClusterName = TestUtils.getUniqueString("test-cluster");
    String fakeMasterControllerUri = TestUtils.getUniqueString("http://fake_uri");

    try (MockD2ServerWrapper mockController = ServiceFactory.getMockD2Server("test-controller", d2ClusterName, d2ServiceName)) {
      String uriPattern = ControllerRoute.MASTER_CONTROLLER.getPath() + ".*cluster_name=" + veniceClusterName + ".*";
      mockController.addResponseForUriPattern(uriPattern, constructMasterControllerResponse(veniceClusterName, fakeMasterControllerUri));
      try (D2ControllerClient d2ControllerClient = new D2ControllerClient(d2ServiceName, veniceClusterName, mockController.getZkAddress(), Optional.empty())) {
        String masterControllerUrl = d2ControllerClient.getMasterControllerUrl();
        Assert.assertEquals(fakeMasterControllerUri, masterControllerUrl);
      }
    }
  }

  @Test
  public void testD2ControllerClientWithExternalD2Client() throws Exception{
    String d2ClusterName = "VeniceRouter";
    String d2ServiceName = "VeniceRouter";
    String veniceClusterName = TestUtils.getUniqueString("test-cluster");
    String fakeMasterControllerUri = TestUtils.getUniqueString("http://fake_uri");

    try (MockD2ServerWrapper mockController = ServiceFactory.getMockD2Server("test-controller", d2ClusterName, d2ServiceName))
    {
      String uriPattern = ControllerRoute.MASTER_CONTROLLER.getPath() + ".*cluster_name=" + veniceClusterName + ".*";
      mockController.addResponseForUriPattern(uriPattern, constructMasterControllerResponse(veniceClusterName, fakeMasterControllerUri));
      D2Client d2Client = new D2ClientBuilder()
                              .setZkHosts(mockController.getZkAddress())
                              .build();
      try {
        D2ClientUtils.startClient(d2Client);
        try (D2ControllerClient d2ControllerClient = new D2ControllerClient(d2ServiceName, veniceClusterName,
            d2Client)) {
          String masterControllerUrl = d2ControllerClient.getMasterControllerUrl();
          Assert.assertEquals(fakeMasterControllerUri, masterControllerUrl);
        }
      } finally {
        D2ClientUtils.shutdownClient(d2Client);
      }
    }
  }

  private FullHttpResponse constructMasterControllerResponse(String clusterName, String url) throws Exception{
    MasterControllerResponse response = new MasterControllerResponse();
    response.setCluster(clusterName);
    response.setUrl(url);

    ByteBuf body = Unpooled.wrappedBuffer(new ObjectMapper().writeValueAsBytes(response));
    FullHttpResponse httpResponse = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, body);
    httpResponse.headers().set(HttpHeaderNames.CONTENT_LENGTH, httpResponse.content().readableBytes());

    return httpResponse;
  }
}
