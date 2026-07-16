package modvalidator;

import modvalidator.report.*;

/**
 * CLI entry point for the mod validator.
 *
 * Usage:
 *   java -jar mod-validator.jar <mod-path> [--json] [--output <file>]
 *   java -jar mod-validator.jar --help
 */
public class ModValidatorCli {

    public static void main(String[] args){
        if(args.length == 0 || args[0].equals("--help") || args[0].equals("-h")){
            printHelp();
            return;
        }

        String modPath = null;
        boolean jsonOutput = false;
        String outputFile = null;

        for(int i = 0; i < args.length; i++){
            switch(args[i]){
                case "--json":
                    jsonOutput = true;
                    break;
                case "--output":
                case "-o":
                    if(i + 1 < args.length){
                        outputFile = args[++i];
                    }else{
                        System.err.println("Error: --output requires a file path");
                        System.exit(1);
                    }
                    break;
                case "--help":
                case "-h":
                    printHelp();
                    return;
                default:
                    if(!args[i].startsWith("-") && modPath == null){
                        modPath = args[i];
                    }else if(args[i].startsWith("-")){
                        System.err.println("Unknown option: " + args[i]);
                        System.exit(1);
                    }
                    break;
            }
        }

        if(modPath == null){
            System.err.println("Error: No mod path specified. Use --help for usage info.");
            System.exit(1);
        }

        String testDataDir = System.getProperty("java.io.tmpdir") + "/modvalidator-test-data";
        ModValidator validator = new ModValidator(testDataDir);
        ReportGenerator reportGen = new ReportGenerator();

        try{
            System.out.println("Validating mod: " + modPath);
            System.out.println("========================================");

            ValidationResult result = validator.validate(modPath);

            // Generate report
            String report = jsonOutput ? reportGen.generateJsonReport(result) : reportGen.generateTextReport(result);
            System.out.println(report);

            // Write to file if requested
            if(outputFile != null){
                reportGen.writeReport(result, outputFile, jsonOutput);
                System.out.println("Report written to: " + outputFile);
            }

            // Exit code: 0 = passed, 1 = failed
            System.exit(result.hasErrors() ? 1 : 0);

        }catch(Exception e){
            System.err.println("Fatal error: " + e.getMessage());
            e.printStackTrace();
            System.exit(2);
        }finally{
            validator.shutdown();
        }
    }

    private static void printHelp(){
        System.out.println("Mindustry Mod Validator - Dynamic JSON/JS Content Tester");
        System.out.println();
        System.out.println("Usage:");
        System.out.println("  java -jar mod-validator.jar <mod-path> [options]");
        System.out.println();
        System.out.println("Arguments:");
        System.out.println("  <mod-path>       Path to mod file (.zip) or directory");
        System.out.println();
        System.out.println("Options:");
        System.out.println("  --json           Output report in JSON format");
        System.out.println("  --output, -o     Write report to file");
        System.out.println("  --help, -h       Show this help message");
        System.out.println();
        System.out.println("Exit codes:");
        System.out.println("  0  Validation passed");
        System.out.println("  1  Validation failed (errors found)");
        System.out.println("  2  Fatal error");
        System.out.println();
        System.out.println("Examples:");
        System.out.println("  java -jar mod-validator.jar ./my-mod");
        System.out.println("  java -jar mod-validator.jar ./my-mod.zip --json --output report.json");
    }
}
