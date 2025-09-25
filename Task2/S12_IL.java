// ──────────────────────────────────────────────────────────────────────────────
// File: S12_IL.java
// Purpose: Instruction-level simulator core implementing S12_IL_Interface
// Notes: This is a clean starter skeleton wired to the spec formats.
//        Fill in the TODOs under executeStep() to implement the ISA behavior.
// ──────────────────────────────────────────────────────────────────────────────

import java.io.*;
import java.util.*;

public class S12_IL implements S12_IL_Interface {
    // ───────────────────────── State & Constants ─────────────────────────
    private static final int MEM_SIZE = 256;        // 8-bit addressing
    private static final int WORD_BITS = 12;        // 12-bit words
    private static final int WORD_MASK = 0xFFF;     // mask to 12 bits
    private static final int ADDR_MASK = 0xFF;      // mask to 8 bits

    private int[] mem = new int[MEM_SIZE];          // 12-bit words per address
    private int pc = 0;                             // 8-bit PC
    private int acc = 0;                            // 12-bit ACC (two's complement)

    // Trace buffer (human-readable lines like "LOAD FF")
    private final List<String> trace = new ArrayList<>();

    // ─────────────────────────── Utilities ───────────────────────────────
    private static String toBinary(int value, int bits) {
        String s = Integer.toBinaryString(value & ((1 << bits) - 1));
        if (s.length() < bits) s = "0".repeat(bits - s.length()) + s;
        return s;
    }

    private static int parse12(String bin12) {
        // Accept exactly 12 characters of 0/1
        if (bin12 == null || bin12.length() != 12 || !bin12.matches("[01]{12}"))
            throw new IllegalArgumentException("Invalid 12-bit binary: " + bin12);
        return Integer.parseInt(bin12, 2) & WORD_MASK;
    }

    private static int parse8(String bin8) {
        if (bin8 == null || bin8.length() != 8 || !bin8.matches("[01]{8}"))
            throw new IllegalArgumentException("Invalid 8-bit binary: " + bin8);
        return Integer.parseInt(bin8, 2) & ADDR_MASK;
    }

    private static String hex2(int x) { return String.format(Locale.ROOT, "%02X", x & 0xFF); }

    // ─────────────────────── Interface Methods ───────────────────────────
    @Override
    public boolean intializeMem(String filename) {
        // Per spec: first line => 8-bit PC, space, 12-bit ACC (binary)
        // Then 256 lines: "AA vvvvvvvvvvvv" (AA hex address, 12-bit binary word)
        Arrays.fill(mem, 0);
        trace.clear();
        try (BufferedReader br = new BufferedReader(new FileReader(filename))) {
            String line;

            // Read header (pc, acc)
            while ((line = br.readLine()) != null) {
                line = stripComment(line).trim();
                if (line.isEmpty()) continue; // skip blanks
                String[] toks = line.split("\\s+");
                if (toks.length < 2) throw new IOException("Header requires PC and ACC binary");
                this.pc = parse8(toks[0]);
                this.acc = parse12(toks[1]);
                break;
            }

            // Read 256 memory lines; allow out-of-order but write by AA
            while ((line = br.readLine()) != null) {
                String cleaned = stripComment(line).trim();
                if (cleaned.isEmpty()) continue;
                String[] toks = cleaned.split("\\s+");
                if (toks.length < 2) continue; // ignore malformed tail
                int aa = Integer.parseInt(toks[0], 16) & ADDR_MASK; // two-hex-digit address
                int word = parse12(toks[1]);
                mem[aa] = word;
            }
            return true;
        } catch (Exception e) {
            System.err.println("initializeMem error: " + e.getMessage());
            return false;
        }
    }

    @Override
    public String[] getProcessorState() {
        // You can extend this later (e.g., include flags). For now: PC, ACC.
        return new String[] { toBinary(pc, 8), toBinary(acc, 12) };
    }

    @Override
    public String getMemState() {
        StringBuilder sb = new StringBuilder();
        for (int a = 0; a < MEM_SIZE; a++) {
            sb.append(hex2(a)).append(' ').append(toBinary(mem[a], WORD_BITS));
            if (a < MEM_SIZE - 1) sb.append(System.lineSeparator());
        }
        return sb.toString();
    }

    @Override
    public String update() {
        // Execute ONE cycle (one instruction) and return a representation
        // Here we return the 12-bit binary of the executed instruction (fits the interface
        // comment), but we ALSO push the human-readable line to `trace` for writeTrace().
        int instr = mem[pc & ADDR_MASK] & WORD_MASK;
        int currPC = pc; // for tracing the instruction that was at this PC

        // Default next PC (can be overridden by branches/jumps)
        pc = (pc + 1) & ADDR_MASK;

        // Decode fields: Top 4 bits = opcode; Low 8 bits = operand/address
        int opcode = (instr >>> 8) & 0xF;
        int operand = instr & 0xFF;

        String mnemonic = decodeMnemonic(opcode);
        String human = mnemonic + " " + hex2(operand);

        // Execute the behavior (TODO: fill in real semantics)
        executeStep(opcode, operand);

        // Record trace line as mnemonic + two hex digits
        trace.add(human);
        return toBinary(instr, WORD_BITS);
    }

    @Override
    public boolean writeMem(String filename) {
        try (PrintWriter out = new PrintWriter(new FileWriter(filename))) {
            out.println(toBinary(pc, 8) + " " + toBinary(acc, 12));
            for (int a = 0; a < MEM_SIZE; a++) {
                out.println(hex2(a) + " " + toBinary(mem[a], WORD_BITS));
            }
            return true;
        } catch (IOException e) {
            System.err.println("writeMem error: " + e.getMessage());
            return false;
        }
    }

    @Override
    public boolean writeTrace(String filename) {
        try (PrintWriter out = new PrintWriter(new FileWriter(filename))) {
            for (String line : trace) out.println(line);
            return true;
        } catch (IOException e) {
            System.err.println("writeTrace error: " + e.getMessage());
            return false;
        }
    }

    // ──────────────────────── Execution Core ─────────────────────────────
    private void executeStep(int opcode, int operand) {
        // Minimal skeleton of likely instructions observed in your .mem files
        // (Complete/adjust as needed for your actual ISA.)
        switch (opcode) {
            case 0x4: // LOAD AA  => ACC = M[AA]
                acc = mem[operand & ADDR_MASK] & WORD_MASK;
                break;
            case 0x5: // STORE AA => M[AA] = ACC
                mem[operand & ADDR_MASK] = acc & WORD_MASK;
                break;
            case 0x6: // LOADI AA => ACC = M[ M[AA] ] (indirect)
                acc = mem[mem[operand & ADDR_MASK] & ADDR_MASK] & WORD_MASK;
                break;
            case 0x3: // ADD AA   => ACC = ACC + M[AA]
                acc = add12(acc, mem[operand & ADDR_MASK]);
                break;
            case 0x2: // SUB AA   => ACC = ACC - M[AA]
                acc = add12(acc, negate12(mem[operand & ADDR_MASK]));
                break;
            case 0x8: // JZ AA    => if ACC == 0 then PC = AA
                if ((acc & WORD_MASK) == 0) pc = operand & ADDR_MASK;
                break;
            case 0xF: // HALT (we'll treat any 0xFxx as HALT; refine if needed)
                // Set PC to itself so repeated update() won't advance.
                pc = (pc - 1) & ADDR_MASK; // hold position
                break;
            default:
                // Unknown opcode — no-op, but you might want to throw.
                // System.err.printf("WARN: Unimplemented opcode 0x%X at %02X\n", opcode, (pc-1)&0xFF);
                break;
        }
        acc &= WORD_MASK; // keep ACC to 12 bits
    }

    private static int add12(int a, int b) {
        return (a + b) & WORD_MASK;
    }

    private static int negate12(int x) {
        // two's complement 12-bit negate
        return ((~x) + 1) & WORD_MASK;
    }

    private static String stripComment(String line) {
        int i = line.indexOf('#');
        return (i >= 0 ? line.substring(0, i) : line);
    }

    private static String decodeMnemonic(int opcode) {
        switch (opcode) {
            case 0x4: return "LOAD";   // 0100
            case 0x5: return "STORE";  // 0101
            case 0x6: return "LOADI";  // 0110 (indirect)
            case 0x3: return "ADD";    // 0011
            case 0x2: return "SUB";    // 0010
            case 0x8: return "JZ";     // 1000
            case 0xF: return "HALT";   // 1111 (placeholder)
            default:  return String.format("OP%X", opcode);
        }
    }
}

