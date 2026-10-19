// ---------- bootstrap ----------
// SP = 256
@256
D=A
@SP
M=D
// call Sys.init 0
// push return address
@Sys.init$ret.0
D=A
@SP
A=M
M=D
@SP
M=M+1
// push LCL
@LCL
D=M
@SP
A=M
M=D
@SP
M=M+1
// push ARG
@ARG
D=M
@SP
A=M
M=D
@SP
M=M+1
// push THIS
@THIS
D=M
@SP
A=M
M=D
@SP
M=M+1
// push THAT
@THAT
D=M
@SP
A=M
M=D
@SP
M=M+1
// ARG = SP - 5
@SP
D=M
@5
D=D-A
@ARG
M=D
// LCL = SP
@SP
D=M
@LCL
M=D
// goto Sys.init
@Sys.init
0;JMP
(Sys.init$ret.0)
// ========== Main.vm ==========
// function Main.fibonacci 0
(Main.fibonacci)
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
// push constant 2
@2
D=A
@SP
A=M
M=D
@SP
M=M+1
// lt
@SP
AM=M-1
D=M
A=A-1
D=M-D
M=-1
@COMP_TRUE_0
D;JLT
@SP
A=M-1
M=0
(COMP_TRUE_0)
// if-goto N_LT_2
@SP
AM=M-1
D=M
@Main.fibonacci$N_LT_2
D;JNE
// goto N_GE_2
@Main.fibonacci$N_GE_2
0;JMP
// label N_LT_2
(Main.fibonacci$N_LT_2)
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
// label N_GE_2
(Main.fibonacci$N_GE_2)
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
// push constant 2
@2
D=A
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
// call Main.fibonacci 1
// push return address
@Main.fibonacci$ret.1
D=A
@SP
A=M
M=D
@SP
M=M+1
// push LCL
@LCL
D=M
@SP
A=M
M=D
@SP
M=M+1
// push ARG
@ARG
D=M
@SP
A=M
M=D
@SP
M=M+1
// push THIS
@THIS
D=M
@SP
A=M
M=D
@SP
M=M+1
// push THAT
@THAT
D=M
@SP
A=M
M=D
@SP
M=M+1
// ARG = SP - 6
@SP
D=M
@6
D=D-A
@ARG
M=D
// LCL = SP
@SP
D=M
@LCL
M=D
// goto Main.fibonacci
@Main.fibonacci
0;JMP
(Main.fibonacci$ret.1)
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
// push constant 1
@1
D=A
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
// call Main.fibonacci 1
// push return address
@Main.fibonacci$ret.2
D=A
@SP
A=M
M=D
@SP
M=M+1
// push LCL
@LCL
D=M
@SP
A=M
M=D
@SP
M=M+1
// push ARG
@ARG
D=M
@SP
A=M
M=D
@SP
M=M+1
// push THIS
@THIS
D=M
@SP
A=M
M=D
@SP
M=M+1
// push THAT
@THAT
D=M
@SP
A=M
M=D
@SP
M=M+1
// ARG = SP - 6
@SP
D=M
@6
D=D-A
@ARG
M=D
// LCL = SP
@SP
D=M
@LCL
M=D
// goto Main.fibonacci
@Main.fibonacci
0;JMP
(Main.fibonacci$ret.2)
// add
@SP
AM=M-1
D=M
A=A-1
M=D+M
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
// ========== Sys.vm ==========
// function Sys.init 0
(Sys.init)
// push constant 4
@4
D=A
@SP
A=M
M=D
@SP
M=M+1
// call Main.fibonacci 1
// push return address
@Main.fibonacci$ret.3
D=A
@SP
A=M
M=D
@SP
M=M+1
// push LCL
@LCL
D=M
@SP
A=M
M=D
@SP
M=M+1
// push ARG
@ARG
D=M
@SP
A=M
M=D
@SP
M=M+1
// push THIS
@THIS
D=M
@SP
A=M
M=D
@SP
M=M+1
// push THAT
@THAT
D=M
@SP
A=M
M=D
@SP
M=M+1
// ARG = SP - 6
@SP
D=M
@6
D=D-A
@ARG
M=D
// LCL = SP
@SP
D=M
@LCL
M=D
// goto Main.fibonacci
@Main.fibonacci
0;JMP
(Main.fibonacci$ret.3)
// label END
(Sys.init$END)
// goto END
@Sys.init$END
0;JMP
// ---------- end of program: halt ----------
(END_OF_PROGRAM)
@END_OF_PROGRAM
0;JMP
