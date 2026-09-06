package com.andemar.pdfcleaner.cli;

import com.andemar.pdfcleaner.config.Aggressiveness;
import com.andemar.pdfcleaner.config.CleanerConfig;
import com.andemar.pdfcleaner.config.LayoutMode;
import com.andemar.pdfcleaner.pipeline.CleanerPipelineOrchestrator;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Command-line interface entry point for clean2epub:
 * clean2epub input.pdf [options]
 */
public class Clean2EpubCli {

    public static void main(String[] args) {
        int exitCode = run(args);
        if (exitCode != 0 && !Boolean.getBoolean("pdfcleaner.no-exit")) {
            System.exit(exitCode);
        }
    }

    public static int run(String[] args) {
        if (args == null || args.length == 0 || hasFlag(args, "-h", "--help")) {
            printUsage();
            return 0;
        }

        String inputPath = null;
        String outputPath = null;
        CleanerConfig config = new CleanerConfig();

        try {
            for (int i = 0; i < args.length; i++) {
                String arg = args[i];

                if (arg.startsWith("--aggressiveness=")) {
                    String val = arg.substring("--aggressiveness=".length()).toUpperCase();
                    config.setAggressiveness(Aggressiveness.valueOf(val));
                } else if (arg.startsWith("--layout-mode=")) {
                    String val = arg.substring("--layout-mode=".length()).toUpperCase();
                    if (val.contains("FIXED")) {
                        config.setLayoutMode(LayoutMode.FIXED_ISH);
                    } else {
                        config.setLayoutMode(LayoutMode.REFLOWABLE);
                    }
                } else if (arg.startsWith("--text-structure=") || arg.startsWith("--structure=")) {
                    String val = arg.contains("=") ? arg.substring(arg.indexOf('=') + 1).toUpperCase() : "";
                    if (val.contains("EXACT") || val.contains("RAW")) {
                        config.setTextStructure(com.andemar.pdfcleaner.config.TextStructureMode.EXACT);
                    } else {
                        config.setTextStructure(com.andemar.pdfcleaner.config.TextStructureMode.SEMANTIC);
                    }
                } else if (arg.equals("--exact-structure")) {
                    config.setTextStructure(com.andemar.pdfcleaner.config.TextStructureMode.EXACT);
                } else if (arg.equals("--semantic-structure")) {
                    config.setTextStructure(com.andemar.pdfcleaner.config.TextStructureMode.SEMANTIC);
                } else if (arg.startsWith("--report-path=")) {
                    config.setReportPath(arg.substring("--report-path=".length()));
                } else if (arg.startsWith("--ml-endpoint-url=")) {
                    config.setMlEndpointUrl(arg.substring("--ml-endpoint-url=".length()));
                } else if (arg.startsWith("--whitelist-phrase=")) {
                    config.addWhitelistPhrase(arg.substring("--whitelist-phrase=".length()));
                } else if (arg.startsWith("--title=")) {
                    config.setTitle(arg.substring("--title=".length()));
                } else if (arg.startsWith("--author=")) {
                    config.setAuthor(arg.substring("--author=".length()));
                } else if (arg.equals("--dry-run")) {
                    config.setDryRun(true);
                } else if (arg.equals("-o") || arg.equals("--output")) {
                    if (i + 1 < args.length) {
                        outputPath = args[++i];
                    } else {
                        System.err.println("Error: Missing argument for " + arg);
                        return 1;
                    }
                } else if (arg.startsWith("-")) {
                    System.err.println("Unknown option: " + arg);
                    printUsage();
                    return 1;
                } else {
                    if (inputPath == null) {
                        inputPath = arg;
                    } else {
                        System.err.println("Unexpected additional argument: " + arg);
                    }
                }
            }

            if (inputPath == null) {
                System.err.println("Error: No input PDF file specified.");
                printUsage();
                return 1;
            }

            File inputFile = new File(inputPath);
            File outputFile = null;
            if (!config.isDryRun() || outputPath != null) {
                outputFile = outputPath != null ? new File(outputPath) : CleanerPipelineOrchestrator.deriveDefaultOutputFile(inputFile);
            }

            System.out.println("Processing: " + inputFile.getAbsolutePath());
            System.out.println("Layout Mode: " + config.getLayoutMode());
            System.out.println("Text Structure: " + config.getTextStructure());
            System.out.println("Aggressiveness: " + config.getAggressiveness());
            if (config.isDryRun()) {
                System.out.println("Mode: DRY RUN (no modifications written)");
            }

            CleanerPipelineOrchestrator orchestrator = new CleanerPipelineOrchestrator();
            CleanerPipelineOrchestrator.CleanResult result = orchestrator.processFile(inputFile, outputFile, config);

            System.out.println("\n" + result.getReport().toFormattedSummary());
            if (!config.isDryRun()) {
                System.out.println("Cleaned EPUB successfully created: " + outputFile.getAbsolutePath());
            }

            return 0;
        } catch (Exception e) {
            System.err.println("Error processing PDF: " + e.getMessage());
            e.printStackTrace(System.err);
            return 1;
        }
    }

    private static boolean hasFlag(String[] args, String... flags) {
        for (String a : args) {
            for (String f : flags) {
                if (a.equals(f)) return true;
            }
        }
        return false;
    }

    private static void printUsage() {
        System.out.println("""
                clean2epub - PDF Cleaner and EPUB Conversion Tool
                
                Usage:
                  clean2epub <input.pdf> [options]
                
                Options:
                  -o, --output <file.epub>       Specify output EPUB file path (default: input.cleaned.epub)
                  --aggressiveness=<mode>        CONSERVATIVE | BALANCED | AGGRESSIVE (default: BALANCED)
                  --layout-mode=<mode>           REFLOWABLE | FIXEDISH (default: REFLOWABLE)
                  --text-structure=<mode>        SEMANTIC | EXACT (default: SEMANTIC)
                                                 SEMANTIC: Refines language flow, commas, semicolons, capital letters, dialogue
                                                 EXACT: Preserves exact original line breaks, formatting and punctuation
                  --exact-structure              Shorthand flag to keep exact original text structure
                  --semantic-structure           Shorthand flag to apply semantic language structure
                  --title=<title>                Specify custom book title (default: derived from input file name)
                  --author=<author>              Specify book author (default: from PDF metadata or Unknown)
                  --dry-run                      Run spam detection and output diagnostic report without writing EPUB
                  --report-path=<file.txt>       Write spam detection report to text file
                  --whitelist-phrase=<phrase>    Preserve specific header text or chapter title
                  --ml-endpoint-url=<url>        Optional HTTP microservice for ML/CLIP logo classification
                  -h, --help                     Display this help message
                
                Examples:
                  clean2epub my_book.pdf
                  clean2epub book.pdf --text-structure=exact -o exact.epub
                  clean2epub book.pdf --layout-mode=fixedish -o clean.epub
                  clean2epub manual.pdf --dry-run --report-path=report.txt
                """);
    }
}
