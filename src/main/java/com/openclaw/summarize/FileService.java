package com.openclaw.summarize;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@Service
public class FileService {

    public void saveRows(List<String> rows, String fileName) {
        try {
            Files.write(Path.of(fileName), rows);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }


    public List<String> readRows(String fileName) {
        try {
            return Files.readAllLines(Path.of(fileName));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }


}
