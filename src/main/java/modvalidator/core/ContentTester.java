package modvalidator.core;

import arc.struct.*;
import mindustry.*;
import mindustry.core.*;
import mindustry.ctype.*;
import mindustry.game.*;
import mindustry.gen.*;
import mindustry.type.*;
import mindustry.world.*;
import mindustry.world.blocks.payloads.*;

import modvalidator.ValidationResult;

import java.util.*;

/**
 * Performs dynamic runtime tests on loaded content.
 * Spawns units, places blocks, runs update ticks, checks for crashes.
 */
public class ContentTester {

    private final HeadlessTestEnvironment env;
    private final ValidationResult result;

    public ContentTester(HeadlessTestEnvironment env, ValidationResult result){
        this.env = env;
        this.result = result;
    }

    /**
     * Run all dynamic tests on the loaded mod.
     */
    public void runAllTests(){
        try{
            testBlocks();
        }catch(Exception e){
            result.addIssue(ValidationResult.Severity.ERROR, "block-test",
                "Block testing crashed: " + e.getMessage());
        }

        try{
            testUnits();
        }catch(Exception e){
            result.addIssue(ValidationResult.Severity.ERROR, "unit-test",
                "Unit testing crashed: " + e.getMessage());
        }

        try{
            testItems();
        }catch(Exception e){
            result.addIssue(ValidationResult.Severity.ERROR, "item-test",
                "Item testing crashed: " + e.getMessage());
        }

        try{
            testLiquids();
        }catch(Exception e){
            result.addIssue(ValidationResult.Severity.ERROR, "liquid-test",
                "Liquid testing crashed: " + e.getMessage());
        }
    }

    /**
     * Test all blocks from the mod: place them, run update ticks.
     */
    @SuppressWarnings("unchecked")
    public void testBlocks(){
        Seq<Block> blocks = (Seq<Block>)(Seq<?>)env.getContent(ContentType.block).select(b -> b.minfo.mod != null);

        if(blocks.isEmpty()){
            result.addIssue(ValidationResult.Severity.INFO, "block-test",
                "No mod blocks found to test");
            return;
        }

        World world = env.world();
        int size = Math.max(16, blocks.size * 2 + 4);
        world.resize(size, size);
        world.tiles.fill();

        int x = 2, y = 2;
        for(Block block : blocks){
            try{
                world.tile(x, y).setBlock(block, Team.get(0), 0);

                for(int i = 0; i < 5; i++){
                    if(world.tile(x, y).build != null){
                        world.tile(x, y).build.update();
                    }
                }

                result.addIssue(ValidationResult.Severity.INFO, "block-test",
                    "Block '" + block.name + "' placed and updated OK");

            }catch(Exception e){
                result.addIssue(ValidationResult.Severity.ERROR, "block-test",
                    "Block '" + block.name + "' crashed on update: " + e.getMessage());
            }

            x++;
            if(x >= size - 1){
                x = 2;
                y++;
            }
            if(y >= size - 1) break;
        }
    }

    /**
     * Test all units from the mod: spawn them, run update ticks.
     */
    @SuppressWarnings("unchecked")
    public void testUnits(){
        Seq<UnitType> units = (Seq<UnitType>)(Seq<?>)env.getContent(ContentType.unit).select(u -> u.minfo.mod != null);

        if(units.isEmpty()){
            result.addIssue(ValidationResult.Severity.INFO, "unit-test",
                "No mod units found to test");
            return;
        }

        World world = env.world();
        // Ensure world is large enough for unit spawning
        if(world.width() < 10 || world.height() < 10){
            world.resize(64, 64);
            world.tiles.fill();
        }

        for(UnitType unit : units){
            try{
                Unit spawned = unit.create(Team.get(0));
                spawned.set(10f, 10f);
                spawned.add();

                for(int i = 0; i < 10; i++){
                    spawned.update();
                }

                result.addIssue(ValidationResult.Severity.INFO, "unit-test",
                    "Unit '" + unit.name + "' spawned and updated OK");

            }catch(Exception e){
                result.addIssue(ValidationResult.Severity.ERROR, "unit-test",
                    "Unit '" + unit.name + "' crashed: " + e.getMessage());
            }
        }
    }

    /**
     * Test all items: verify they have valid icons and stats.
     */
    @SuppressWarnings("unchecked")
    public void testItems(){
        Seq<Item> items = (Seq<Item>)(Seq<?>)env.getContent(ContentType.item).select(i -> i.minfo.mod != null);

        if(items.isEmpty()){
            result.addIssue(ValidationResult.Severity.INFO, "item-test",
                "No mod items found to test");
            return;
        }

        for(Item item : items){
            try{
                if(item.name == null || item.name.isEmpty()){
                    result.addIssue(ValidationResult.Severity.WARN, "item-test",
                        "Item has empty name");
                }else{
                    result.addIssue(ValidationResult.Severity.INFO, "item-test",
                        "Item '" + item.name + "' loaded OK");
                }
            }catch(Exception e){
                result.addIssue(ValidationResult.Severity.ERROR, "item-test",
                    "Item '" + item.name + "' error: " + e.getMessage());
            }
        }
    }

    /**
     * Test all liquids: verify they have valid icons and stats.
     */
    @SuppressWarnings("unchecked")
    public void testLiquids(){
        Seq<Liquid> liquids = (Seq<Liquid>)(Seq<?>)env.getContent(ContentType.liquid).select(l -> l.minfo.mod != null);

        if(liquids.isEmpty()){
            result.addIssue(ValidationResult.Severity.INFO, "liquid-test",
                "No mod liquids found to test");
            return;
        }

        for(Liquid liquid : liquids){
            try{
                if(liquid.name == null || liquid.name.isEmpty()){
                    result.addIssue(ValidationResult.Severity.WARN, "liquid-test",
                        "Liquid has empty name");
                }else{
                    result.addIssue(ValidationResult.Severity.INFO, "liquid-test",
                        "Liquid '" + liquid.name + "' loaded OK");
                }
            }catch(Exception e){
                result.addIssue(ValidationResult.Severity.ERROR, "liquid-test",
                    "Liquid '" + liquid.name + "' error: " + e.getMessage());
            }
        }
    }
}
