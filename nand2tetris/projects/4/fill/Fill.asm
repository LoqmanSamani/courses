// This file is part of www.nand2tetris.org
// and the book "The Elements of Computing Systems"
// by Nisan and Schocken, MIT Press.
// File name: projects/4/Fill.asm

// Runs an infinite loop that listens to the keyboard input. 
// When a key is pressed (any key), the program blackens the screen,
// i.e. writes "black" in every pixel. When no key is pressed, 
// the screen should be cleared.

// Pseudocode:
//   forever:
//       color = (KBD != 0) ? -1 : 0       // -1 is 1111111111111111 = 16 black pixels
//       for addr = SCREEN .. SCREEN+8191:
//           RAM[addr] = color

(LOOP)
    @KBD
    D=M             // D = keyboard register (0 if no key held)
    @BLACK
    D;JNE           // key pressed -> paint black

    @color
    M=0             // color = white
    @PAINT
    0;JMP

(BLACK)
    @color
    M=-1            // color = black (all 16 bits set)

(PAINT)
    @SCREEN
    D=A             // D = 16384, first word of the screen map
    @addr
    M=D             // addr = SCREEN

(PAINTLOOP)
    @addr
    D=M             // D = addr
    @KBD
    D=D-A           // D = addr - 24576   (KBD sits right after the last screen word)
    @LOOP
    D;JGE           // if addr >= 24576 the screen is done -> go re-read the keyboard

    @color
    D=M             // D = color
    @addr
    A=M             // A = addr   (pointer dereference: select RAM[addr])
    M=D             // RAM[addr] = color

    @addr
    M=M+1           // addr = addr + 1
    @PAINTLOOP
    0;JMP
