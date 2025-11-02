package com.emoney.cs.common.designpattern.yalco._01_facade.ex02;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

class FileWriter {
    public void writeFile(String filePath, String content)
            throws IOException {
        Files.write(Paths.get(filePath), content.getBytes());
    }
}