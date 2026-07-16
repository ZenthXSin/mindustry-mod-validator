package modvalidator;

import arc.files.*;
import arc.struct.*;
import mindustry.*;
import mindustry.ctype.*;
import mindustry.mod.*;
import mindustry.mod.Mods.*;

import modvalidator.core.*;
import modvalidator.report.*;

import java.io.*;
import java.nio.file.*;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;

public class ModValidator {

    private final HeadlessTestEnvironment env;
    private final ReportGenerator reportGen;

    public ModValidator(String testDataDir){
        this.env = new HeadlessTestEnvironment(testDataDir);
        this.reportGen = new ReportGenerator();
    }

    public ValidationResult validate(String modPath){
        ValidationResult result = new ValidationResult();
        result.modPath = modPath;
        result.loadSuccess = true;

        long startTime = System.currentTimeMillis();

        try{
            System.out.println("[ModValidator] Initializing headless environment with mod: " + modPath);
            env.initialize(modPath);

            LoadedMod mod = env.getImportedMod();
            if(mod != null){
                result.modName = mod.name;
            }else{
                for(LoadedMod m : env.getLoadedMods()){
                    if(m.name != null && !m.name.isEmpty()){
                        result.modName = m.name;
                        break;
                    }
                }
            }

            long loadTime = System.currentTimeMillis() - startTime;
            result.loadTimeMs = loadTime;
            System.out.println("[ModValidator] Environment + mod ready in " + loadTime + "ms");

            if(env.hasContentErrors()){
                Map<String, List<String>> errors = env.getContentErrors();
                for(Map.Entry<String, List<String>> entry : errors.entrySet()){
                    for(String error : entry.getValue()){
                        result.addIssue(ValidationResult.Severity.ERROR, "content-parse", error);
                    }
                }
            }

            for(String err : env.getErrorLogs()){
                result.addIssue(ValidationResult.Severity.ERROR, "runtime-log", err);
            }
            for(String warn : env.getWarnLogs()){
                result.addIssue(ValidationResult.Severity.WARN, "runtime-log", warn);
            }

            System.out.println("[ModValidator] Running dynamic tests...");
            long testStart = System.currentTimeMillis();

            ContentTester tester = new ContentTester(env, result);
            tester.runAllTests();

            result.testTimeMs = System.currentTimeMillis() - testStart;
            System.out.println("[ModValidator] Tests completed in " + result.testTimeMs + "ms");

        }catch(FileNotFoundException e){
            result.addIssue(ValidationResult.Severity.CRITICAL, "load",
                "Mod file not found: " + e.getMessage());
        }catch(TimeoutException e){
            result.addIssue(ValidationResult.Severity.CRITICAL, "load",
                "Loading timed out: " + e.getMessage());
        }catch(Exception e){
            result.addIssue(ValidationResult.Severity.CRITICAL, "load",
                "Unexpected error: " + e.getClass().getSimpleName() + ": " + e.getMessage());
            StringWriter sw = new StringWriter();
            e.printStackTrace(new PrintWriter(sw));
            result.addIssue(ValidationResult.Severity.INFO, "debug",
                "Stack trace: " + sw.toString().substring(0, Math.min(2000, sw.toString().length())));
        }

        return result;
    }

    public void shutdown(){
        env.dispose();
    }
}
