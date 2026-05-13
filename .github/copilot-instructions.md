# Copilot instructions for ffmasm

## Project overview

ffmasm is a Java library for emitting machine code directly like native assembly compiler.
This project uses Maven and JUnit Jupiter.

When adding or modifying instruction methods, preserve the existing design of the relevant architecture-specific builder classes (`*Builder.java`) which are located at `src/main/java/com/yasuenag/ffmasm/<arch>`.

## General rules for adding instruction methods

### One instruction, one method

- Strictly follow the "one instruction mnemonic and encoding form, one public method family" rule.
    - It is allowed to support multiple operand forms in one public method only when they are variants of the same instruction mnemonic.
    - It is allowed to support multiple operand sizes in one public method only when they are size variants of the same instruction mnemonic and share the same semantic operation.
    - For example, `MOV r/m64, r64` and `MOV r/m32, r32` can be handled by one public method if the existing API style does so.
    - For example, register-source and memory-source forms of the same instruction may share one public method if the existing API style does so.
    - If operand direction or instruction semantics differ, use separate public methods consistent with the existing API style.
    - Do not combine different instruction mnemonics into one public method.
    - Do not combine multiple machine instructions into one public method.
- Do not add a single public method that emits multiple machine instructions.
- Do not hide instruction sequences behind a method whose name looks like a single instruction.
- If a requested feature requires multiple machine instructions, ask for clarification or implement only the requested single instruction method.

Good:

```java
builder.mov(...);
builder.add(...);
builder.ret();
```

Bad:

```java
builder.loadAndAdd(...); // emits mov + add
```

### Reuse existing encoding logic

- Before adding new encoding logic, inspect existing methods in the same builder class.
- Reuse existing helper methods for:
    - opcode emission
    - prefix emission
    - REX prefix handling
    - ModRM/SIB encoding
    - displacement emission
    - immediate value emission
- Do not duplicate instruction encoding logic when an existing helper method or existing instruction implementation can be reused.
- Prefer small private helper methods when several new instruction methods share the same encoding form.
- Keep private helpers architecture-specific unless the logic is already shared elsewhere in the project.

### Place methods in the appropriate Builder class

- Define each instruction method in the builder class that corresponds to the target instruction set or architecture.
    - Add methods to the AMD64 builder class for AMD64 instructions.
    - Add methods to appropriate sub classes of `AMD64AsmBuilder` such as `AVXAsmBuilder` if adds extended instructions such as AVX.
    - Add methods to the AArch64 builder class for AArch64 instructions.
- Match the existing API style, including:
    - method naming
    - operand order
    - return type
    - exception style
    - use of `OptionalInt` or other operand helper types

### Javadoc requirements

- Every new public instruction method must have a Javadoc comment.
- The Javadoc must describe the instruction, not only restate the method name.
- Follow the style of existing Javadoc comments in the same builder class.
- Include relevant instruction information like following. They are based on official documents such as [Software Developer's Manual by Intel](https://www.intel.com/content/www/us/en/developer/articles/technical/intel-sdm.html), [A64 Instruction Set Architecture Guide by Arm](https://developer.arm.com/documentation/102374/0103/).
    - instruction mnemonic
    - operand meaning
    - supported operand forms
    - effect on destination operand
    - relevant flags behavior, if applicable
    - unsupported operand sizes or forms, if applicable
- Include `@param` entries for all parameters.
- Include `@return` when existing builder methods do so.
- Include `@throws` when the method validates operands and can throw an exception.

Example style:

```java
  /**
   * Compare A register with r/m.
   * If equal, ZF is set and r is loaded into r/m. Else, clear ZF and load r/m into A register.<br>
   *   Opcode: REX.W + 0F B1/r (64 bit)<br>
   *                   0F B1/r (32 bit)<br>
   *                   0F B1/r (16 bit)<br>
   *                   0F B0/r ( 8 bit)<br>
   *   Instruction: CMPXCHG r/m, r<br>
   *   Op/En: MR
   *
   * @param r register to be set if r/m equals A register
   * @param m register or memory address to be compared with A register
   * @param disp Displacement
   * @return This instance
   */
```

### Test requirements

- Add at least one test for every new public instruction method into appropriate test classes located at `src/test/java/com/yasuenag/ffmasm/test/<arch>`.
- Tests must use JUnit Jupiter, consistent with the existing test code.
- Prefer behavior-oriented tests when the existing test suite executes generated code.
- Do not introduce a new test framework.

### Copyright year

- For every file edited in the change, inspect the copyright notice at the top of the file.
- Determine the current calendar year from the system date.
- If the file has a copyright notice, update the ending copyright year to the current calendar year.
- Preserve the original starting year.
- Preserve the existing copyright notice style.
- Do not update copyright notices in files that are not otherwise edited for the requested change.
- Do not add a copyright notice to files that do not already have one, unless explicitly requested.

Examples, assuming the current calendar year is 2026:

```text
Copyright (C) 2022, 2025, Yasumasa Suenaga
```

must become:

```text
Copyright (C) 2022, 2026, Yasumasa Suenaga
```

### Validation

Before opening a pull request, run:

```bash
mvn test
```

If the change affects packaging or build configuration, also run:

```bash
mvn package
```

Include the validation result in the pull request description.

## Pull request expectations

- Keep changes focused on the requested instruction methods.
- Do not perform unrelated refactoring.
- Do not reformat unrelated files.
- Commit production code and tests together.
- The pull request description must include:
    - summary of added instruction methods
    - test coverage added
    - validation command results
    - any unsupported operands or intentional limitations
    - "Generated by" section containing exactly `GitHub Copilot`
