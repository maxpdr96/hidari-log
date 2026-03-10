package com.hidari.log.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class TailServiceTest {

    private TailService tailService;
    private File tempFile;

    @BeforeEach
    void setUp() throws IOException {
        tailService = new TailService();
        tempFile = File.createTempFile("test-tail", ".log");
        try (FileWriter fw = new FileWriter(tempFile)) {
            fw.write("2023-01-01 INFO Start\n");
            fw.write("2023-01-01 ERROR Boom\n");
            fw.write("2023-01-01 DEBUG Wait\n");
        }
    }

    @AfterEach
    void tearDown() {
        tailService.stop();
        if (tempFile != null && tempFile.exists()) {
            tempFile.delete();
        }
    }

    @Test
    void testTailFileNotFound() {
        String result = tailService.tail("nao_existe.log", null, null, null);
        assertTrue(result.contains("Arquivo nao encontrado"));
        assertFalse(tailService.isRunning());
    }

    @Test
    void testTailStartsSuccessfully() throws InterruptedException {
        String result = tailService.tail(tempFile.getAbsolutePath(), null, null, null);
        assertTrue(result.contains("Tail iniciado"));
        assertTrue(tailService.isRunning());

        // Wait a bit and stop
        Thread.sleep(100);
        tailService.stop();
        assertFalse(tailService.isRunning());
    }

    @Test
    void testTailStopsPrevious() throws InterruptedException {
        tailService.tail(tempFile.getAbsolutePath(), null, null, null);
        assertTrue(tailService.isRunning());

        // Call tail again to stop the first one
        String result = tailService.tail(tempFile.getAbsolutePath(), null, null, null);
        assertTrue(result.contains("Tail anterior parado."));
        // As the second call returns early when stopping the first one, the service is stopped
        assertFalse(tailService.isRunning());
    }

    @Test
    void testStop() throws InterruptedException {
        tailService.tail(tempFile.getAbsolutePath(), null, null, null);
        assertTrue(tailService.isRunning());

        tailService.stop();
        assertFalse(tailService.isRunning());
    }
}
