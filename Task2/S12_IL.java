// ──────────────────────────────────────────────────────────────────────────────
// File: S12_IL.java
// Authors: Sean Denny (CSE 432), Diego Chavolla-Ortiz (CSE 432), Alex Anta (ECE 432) Matthew Saxton (CSE 432)
// ──────────────────────────────────────────────────────────────────────────────

import java.io.*;
import java.util.*;

public class S12_IL implements S12_IL_Interface {
    // ───────────────────────── State & Constants ─────────────────────────
    private static final int MEM_SIZE = 256;        // 8-bit addressing
    private static final int WORD_BITS = 12;        // 12-bit words
    private static final int WORD_MASK = 0xFFF;     // mask to 12 bits
    private static final int ADDR_MASK = 0xFF;      // mask to 8 bits
    
    private static int to12(int x) { x &= WORD_MASK; return (x & 0x800) != 0 ? x - 0x1000 : x; } // signed
    private static int u8(int x) { return x & ADDR_MASK; }

    
    private int[] mem = new int[MEM_SIZE];          // 12-bit words per address
    private int pc = 0;                             // 8-bit PC
    private int acc = 0;                            // 12-bit ACC 

    private final List<String> trace = new ArrayList<>();

    private static String toBinary(int value, int bits) {
        String s = Integer.toBinaryString(value & ((1 << bits) - 1));
        if (s.length() < bits) s = "0".repeat(bits - s.length()) + s;
        return s;
    }

    private static int parse12(String bin12) {
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
        Arrays.fill(mem, 0);
        trace.clear();
        try (BufferedReader br = new BufferedReader(new FileReader(filename))) {
            String line;

            while ((line = br.readLine()) != null) {
                line = stripComment(line).trim();
                if (line.isEmpty()) continue; 
                String[] toks = line.split("\\s+");
                if (toks.length < 2) throw new IOException("Header requires PC and ACC binary");
                this.pc = parse8(toks[0]);
                this.acc = parse12(toks[1]);
                break;
            }

            while ((line = br.readLine()) != null) {
                String cleaned = stripComment(line).trim();
                if (cleaned.isEmpty()) continue;
                String[] toks = cleaned.split("\\s+");
                if (toks.length < 2) continue; 
                int aa = Integer.parseInt(toks[0], 16) & ADDR_MASK; 
                int word = parse12(toks[1]);
                mem[aa] = word;
            }
            return true;
        } catch (Exception e) {
            System.out.println("initializeMem error: " + e.getMessage());
            return false;
        }
    }

    @Override
    public String[] getProcessorState() {
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
        int instr = mem[pc & ADDR_MASK] & WORD_MASK;

        pc = (pc + 1) & ADDR_MASK;

        int opcode = (instr >>> 8) & 0xF;
        int operand = instr & 0xFF;

        String mnemonic = decodeMnemonic(opcode);
        String human = mnemonic + " " + hex2(operand);

        executeStep(opcode, operand);

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
            System.out.println("writeMem error: " + e.getMessage());
            return false;
        }
    }

    @Override
    public boolean writeTrace(String filename) {
        try (PrintWriter out = new PrintWriter(new FileWriter(filename))) {
            for (String line : trace) out.println(line);
            return true;
        } catch (IOException e) {
            System.out.println("writeTrace error: " + e.getMessage());
            return false;
        }
    }
    private void executeStep(int opcode, int operand) {
        switch (opcode) {
            case 0x4: { // LOAD
                acc = mem[u8(operand)] & WORD_MASK;
                break;
            }
            case 0x5: { // STORE
                mem[u8(operand)] = acc & WORD_MASK;
                break;
            }
            case 0x6: { // LOADI
                int ptr = mem[u8(operand)] & ADDR_MASK;
                acc = mem[ptr] & WORD_MASK;
                break;
            }
            case 0x7: { // STOREI   <-- missing before
                int ptr = mem[u8(operand)] & ADDR_MASK;
                mem[ptr] = acc & WORD_MASK;
                break;
            }
            case 0x3: { // ADD
                acc = (acc + (mem[u8(operand)] & WORD_MASK)) & WORD_MASK;
                break;
            }
            case 0x2: { // SUB
                acc = (acc - (mem[u8(operand)] & WORD_MASK)) & WORD_MASK;
                break;
            }
            case 0x8: { // JZ
                // Zero test on signed-normalized value (0 is same signed/unsigned)
                if ( (acc & WORD_MASK) == 0 ) pc = u8(operand);
                break;
            }
            case 0x9: { // JN       <-- missing before
                // Negative if bit 11 set after normalization
                if ( to12(acc) < 0 ) pc = u8(operand);
                break;
            }
            case 0xA: { // JMP      <-- missing before
                pc = u8(operand);
                break;
            }
            case 0xF: { // HALT
                // Keep PC on HALT instruction (or back up one if you prefer).
                pc = (pc - 1) & ADDR_MASK;  // matches your earlier behavior
                break;
            }
            default:
                // NOP for unknown opcodes (or throw if you prefer strictness)
                break;
        }
        acc &= WORD_MASK; // keep ACC 12-bit after every step
    }

    private static String stripComment(String line) {
        int i = line.indexOf('#');
        return (i >= 0 ? line.substring(0, i) : line);
    }

    private static String decodeMnemonic(int opcode) {
        switch (opcode) {
            case 0x4: return "LOAD";   // 0100
            case 0x5: return "STORE";  // 0101
            case 0x6: return "LOADI";  // 0110 
            case 0x3: return "ADD";    // 0011
            case 0x2: return "SUB";    // 0010
            case 0x8: return "JZ";     // 1000
            case 0xF: return "HALT";   // 1111 
            default:  return String.format("OP%X", opcode);
        }
    }
}

