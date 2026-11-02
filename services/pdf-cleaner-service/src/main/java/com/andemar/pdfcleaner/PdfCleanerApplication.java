package com.andemar.pdfcleaner;

import com.andemar.pdfcleaner.cli.Clean2EpubCli;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class PdfCleanerApplication {

    public static void main(String[] args) {
        // If arguments start with a file or CLI flags (-o, --dry-run, -h, etc.), run CLI mode
        if (args != null && args.length > 0 && !isServerFlag(args)) {
            System.setProperty("pdfcleaner.no-exit", "true");
            Clean2EpubCli.main(args);
        } else {
            // Otherwise start Spring Boot HTTP Web API
            SpringApplication.run(PdfCleanerApplication.class, args);
        }
    }

    private static boolean isServerFlag(String[] args) {
        for (String a : args) {
            if (a.equals("--server") || a.startsWith("--server.port") || a.startsWith("--spring.")) {
                return true;
            }
        }
        return false;
    }
}
