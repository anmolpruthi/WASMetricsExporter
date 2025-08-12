package com.score_me.was_metrics_exporter.entities;

import org.junit.Before;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;

import static org.mockito.Mockito.*;

public class ProcessorNodeEntityTest {
    @Mock
    List<String> outgoing;
    @Mock
    List<String> incoming;
    @InjectMocks
    ProcessorNodeEntity processorNodeEntity;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
    }
}
