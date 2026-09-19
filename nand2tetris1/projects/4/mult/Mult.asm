// This file is part of www.nand2tetris.org
// and the book "The Elements of Computing Systems"
// by Nisan and Schocken, MIT Press.
// File name: projects/4/Mult.asm

// Multiplies R0 and R1 and stores the result in R2.
// (R0, R1, R2 refer to RAM[0], RAM[1], and RAM[2], respectively.)
// The algorithm is based on repetitive addition.

// Pseudocode:
//   R2 = 0
//   i  = 0
//   while (i < R1):
//       R2 = R2 + R0
//       i  = i + 1

    @R2
    M=0             // R2 = 0  (the test pre-loads -1 to check we do this)
    @i
    M=0             // i = 0

(LOOP)
    @i
    D=M             // D = i
    @R1
    D=D-M           // D = i - R1
    @END
    D;JGE           // if (i - R1 >= 0) goto END   i.e. i >= R1

    @R0
    D=M             // D = R0
    @R2
    M=D+M           // R2 = R2 + R0

    @i
    M=M+1           // i = i + 1
    @LOOP
    0;JMP           // goto LOOP

(END)
    @END
    0;JMP           // infinite loop: Hack programs never "return"
