// ──────────────────────────────────────────────────────────────────────────────
// File: S12_Sim.java
// Purpose: CLI runner per spec — reads .mem, runs until HALT or -c cycles,
//          writes <base>_memOut and <base>_trace, prints summary to console.
// ──────────────────────────────────────────────────────────────────────────────

import java.io.*;
import java.util.*;

public class S12_Sim {
    private static void usage() {
        System.out.println("Usage: java S12_Sim <memFile> [-o outputBase] [-c cycles]");
    }

    public static void main(String[] args) {
        if (args.length < 1) { usage(); return; }

        String memFile = null;
        String outBase = null; // defaults to input base name without extension
        Integer maxCycles = null; // optional cap

        // ── Parse CLI ──
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "-o":
                    if (i + 1 >= args.length) { usage(); return; }
                    outBase = args[++i];
                    break;
                case "-c":
                    if (i + 1 >= args.length) { usage(); return; }
                    try { maxCycles = Integer.parseInt(args[++i]); }
                    catch (NumberFormatException e) { System.err.println("-c needs an integer"); return; }
                    break;
                default:
                    if (memFile == null) memFile = args[i];
                    else { System.err.println("Unexpected arg: " + args[i]); usage(); return; }
            }
        }
        if (memFile == null) { usage(); return; }

        if (outBase == null) outBase = deriveBase(memFile);
        String memOut = outBase + "_memOut";
        String traceOut = outBase + "_trace";

        // ── Run ──
        S12_IL_Interface cpu = new S12_IL();
        if (!cpu.intializeMem(memFile)) {
            System.err.println("Failed to initialize memory from: " + memFile);
            return;
        }

        int cycles = 0;
        boolean halted = false;
        while (true) {
            String[] st = cpu.getProcessorState(); // [pcBin, accBin]

            // Peek the current instruction to detect HALT (optional – conservative)
            // We rely on update() executing and our IL's HALT semantics.
            String instrBits = cpu.update();
            cycles++;

            // Our IL marks HALT by not advancing PC (see executeStep).
            String[] st2 = cpu.getProcessorState();
            if (st[0].equals(st2[0]) && instrBits.startsWith("1111")) { // opcode 0xF
                halted = true;
            }

            if (halted) break;
            if (maxCycles != null && cycles >= maxCycles) break;
        }

        // ── Output files ──
        if (!cpu.writeMem(memOut)) {
            System.err.println("Failed to write memOut: " + memOut);
        }
        if (!cpu.writeTrace(traceOut)) {
            System.err.println("Failed to write trace: " + traceOut);
        }

        // ── Console summary ──
        String[] finalState = cpu.getProcessorState();
        System.out.println("Cycles Executed: " + cycles);
        System.out.println("PC: 0x" + bin8ToHex2(finalState[0]));
        System.out.println("ACC: 0x" + bin12ToHex3(finalState[1]));
    }

    private static String deriveBase(String path) {
        String p = new java.io.File(path).getName();
        int dot = p.lastIndexOf('.');
        return (dot >= 0 ? p.substring(0, dot) : p);
    }

    private static String bin8ToHex2(String b8) {
        int v = Integer.parseInt(b8, 2) & 0xFF;
        return String.format(Locale.ROOT, "%02X", v);
    }

    private static String bin12ToHex3(String b12) {
        int v = Integer.parseInt(b12, 2) & 0xFFF;
        return String.format(Locale.ROOT, "%03X", v);
    }
}
