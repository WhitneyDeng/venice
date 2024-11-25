package com.linkedin.venice.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;
import static org.testng.Assert.*;

import com.linkedin.venice.meta.HybridStoreConfig;
import com.linkedin.venice.meta.StoreInfo;
import com.linkedin.venice.meta.Version;
import java.util.ArrayList;
import java.util.Optional;
import org.testng.annotations.Test;


public class TestCompactionManager {
  @Test
  public void testFilterStoresForCompaction() {
    CompactionManager mockManager = mock(CompactionManager.class);
    ArrayList<StoreInfo> storeInfoList = new ArrayList<>();
    ArrayList<StoreInfo> compactionReadyStores = new ArrayList<>();

    // Mock StoreInfo instances
    StoreInfo store1 = mock(StoreInfo.class);
    StoreInfo store2 = mock(StoreInfo.class);
    StoreInfo store3 = mock(StoreInfo.class);

    // Mock HybridStoreConfig for the first two StoreInfo instances
    HybridStoreConfig hybridStoreConfig1 = mock(HybridStoreConfig.class);
    HybridStoreConfig hybridStoreConfig2 = mock(HybridStoreConfig.class);
    when(store1.getHybridStoreConfig()).thenReturn(hybridStoreConfig1);
    when(store2.getHybridStoreConfig()).thenReturn(hybridStoreConfig2);
    when(store3.getHybridStoreConfig()).thenReturn(null);

    // Mock version numbers with random numbers
    int store1VersionNumber = 1;
    int store2VersionNumber = 2;
    int store3VersionNumber = 3;
    when(store1.getCurrentVersion()).thenReturn(store1VersionNumber);
    when(store2.getCurrentVersion()).thenReturn(store2VersionNumber);
    when(store3.getCurrentVersion()).thenReturn(store3VersionNumber);

    // Mock Version instances
    Version version1 = mock(Version.class);
    Version version2 = mock(Version.class);
    Version version3 = mock(Version.class);

    // Return Version mocks when getVersion() is called
    when(store1.getVersion(anyInt())).thenReturn(Optional.ofNullable(version1));
    when(store2.getVersion(anyInt())).thenReturn(Optional.ofNullable(version2));
    when(store3.getVersion(anyInt())).thenReturn(Optional.ofNullable(version3));

    // Set createTime for Version mocks
    long currentTime = System.currentTimeMillis();
    long millisecondsPerHour = 60 * 60 * 1000;
    when(version1.getCreatedTime()).thenReturn(currentTime - (25 * millisecondsPerHour)); // 25 hours ago
    when(version2.getCreatedTime()).thenReturn(currentTime - (50 * millisecondsPerHour)); // 50 hours ago
    when(version3.getCreatedTime()).thenReturn(currentTime - (23 * millisecondsPerHour)); // 23 hours ago

    // Add StoreInfo instances to the list
    storeInfoList.add(store1);
    storeInfoList.add(store2);
    storeInfoList.add(store3);

    // Call the real method to test
    doCallRealMethod().when(mockManager).filterStoresForCompaction(any(), any());

    // Test
    mockManager.filterStoresForCompaction(storeInfoList, compactionReadyStores);

    // Assert the expected outcome
    assertEquals(compactionReadyStores.size(), 2);
    assertEquals(compactionReadyStores.contains(store1), true);
    assertEquals(compactionReadyStores.contains(store2), true);
    assertEquals(compactionReadyStores.contains(store3), false);
  }
}
