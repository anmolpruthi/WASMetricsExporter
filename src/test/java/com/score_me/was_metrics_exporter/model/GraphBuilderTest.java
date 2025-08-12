package com.score_me.was_metrics_exporter.model;

import com.score_me.was_metrics_exporter.client.FlowApiClient;
import com.score_me.was_metrics_exporter.entities.ProcessGroupNodeEntity;
import com.score_me.was_metrics_exporter.entities.ProcessorNodeEntity;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Map;

import static org.mockito.Mockito.*;

public class GraphBuilderTest {
    @Mock
    FlowApiClient client;
    @InjectMocks
    GraphBuilder graphBuilder;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    public void testBuildProcessorMap() throws Exception {
        when(client.get(anyString())).thenReturn(null);

        Map<String, ProcessorNodeEntity> result = graphBuilder.buildProcessorMap();
        Assert.assertEquals(Map.of("replaceMeWithExpectedResult", new ProcessorNodeEntity("id", "name", "type")), result);
    }

    @Test
    public void testBuildProcessGroupMap() throws Exception {
        when(client.get(anyString())).thenReturn(null);

        Map<String, ProcessGroupNodeEntity> result = graphBuilder.buildProcessGroupMap();
        Assert.assertEquals(Map.of("replaceMeWithExpectedResult", new ProcessGroupNodeEntity("id", "name")), result);
    }

    @Test
    public void testGetInputOutputPorts() throws Exception {
        when(client.get(anyString())).thenReturn(null);

        int result = graphBuilder.getInputOutputPorts("portType");
        Assert.assertEquals(0, result);
    }

    @Test
    public void testBuildProcessorMapForPg() throws Exception {
        when(client.get(anyString())).thenReturn(null);

        Map<String, ProcessorNodeEntity> result = graphBuilder.buildProcessorMapForPg("pgId");
        Assert.assertEquals(Map.of("replaceMeWithExpectedResult", new ProcessorNodeEntity("id", "name", "type")), result);
    }
}

//Generated with love by TestMe :) Please raise issues & feature requests at: https://weirddev.com/forum#!/testme