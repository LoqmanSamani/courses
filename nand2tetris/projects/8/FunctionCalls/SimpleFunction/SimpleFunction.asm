// ========== SimpleFunction.vm ==========
// function SimpleFunction.test 2
(SimpleFunction.test)
// local 0 = 0
@SP
A=M
M=0
@SP
M=M+1
// local 1 = 0
@SP
A=M
M=0
@SP
M=M+1
// push local 0
@0
D=A
@LCL
A=M+D
D=M
@SP
A=M
M=D
@SP
M=M+1
// push local 1
@1
D=A
@LCL
A=M+D
D=M
@SP
A=M
M=D
@SP
M=M+1
// add
@SP
AM=M-1
D=M
A=A-1
M=D+M
// not
@SP
A=M-1
M=!M
// push argument 0
@0
D=A
@ARG
A=M+D
D=M
@SP
A=M
M=D
@SP
M=M+1
// add
@SP
AM=M-1
D=M
A=A-1
M=D+M
// push argument 1
@1
D=A
@ARG
A=M+D
D=M
@SP
A=M
M=D
@SP
M=M+1
// sub
@SP
AM=M-1
D=M
A=A-1
M=M-D
// return
// R14 = LCL (frame pointer)
@LCL
D=M
@R14
M=D
// R15 = *(frame-5) (return address)
@5
A=D-A
D=M
@R15
M=D
// *ARG = return value
@SP
AM=M-1
D=M
@ARG
A=M
M=D
// SP = ARG + 1
@ARG
D=M+1
@SP
M=D
// THAT = *(--frame)
@R14
AM=M-1
D=M
@THAT
M=D
// THIS = *(--frame)
@R14
AM=M-1
D=M
@THIS
M=D
// ARG = *(--frame)
@R14
AM=M-1
D=M
@ARG
M=D
// LCL = *(--frame)
@R14
AM=M-1
D=M
@LCL
M=D
// goto return address
@R15
A=M
0;JMP
// ---------- end of program: halt ----------
(END_OF_PROGRAM)
@END_OF_PROGRAM
0;JMP
