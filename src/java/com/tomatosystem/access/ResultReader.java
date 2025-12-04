package com.tomatosystem.access;

import java.io.BufferedReader;
import java.io.FileReader;

public class ResultReader {
    
    public static void readResult(String path) {
        try (BufferedReader br = new BufferedReader(new FileReader(path))) {
            String line;
            while ((line = br.readLine()) != null) {
                System.out.println(line);
            }
        } catch (Exception e) {
            System.out.println("결과 읽기 실패: " + e.getMessage());
            e.printStackTrace();
        }
    }
}


