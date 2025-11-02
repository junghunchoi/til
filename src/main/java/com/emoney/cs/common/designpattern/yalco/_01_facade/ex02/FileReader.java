package com.emoney.cs.common.designpattern.yalco._01_facade.ex02;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

class FileReader {
    public String readFile(String filePath)
            throws IOException {
        return new String(Files.readAllBytes(Paths.get(filePath)));
    }
}
