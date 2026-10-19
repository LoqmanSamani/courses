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
// ========== Class1.vm ==========
// function Class1.set 0
(Class1.set)
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
// pop static 0
@SP
AM=M-1
D=M
@Class1.0
M=D
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
// pop static 1
@SP
AM=M-1
D=M
@Class1.1
M=D
// push constant 0
@0
D=A
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
// function Class1.get 0
(Class1.get)
// push static 0
@Class1.0
D=M
@SP
A=M
M=D
@SP
M=M+1
// push static 1
@Class1.1
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
// ========== Class2.vm ==========
// function Class2.set 0
(Class2.set)
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
// pop static 0
@SP
AM=M-1
D=M
@Class2.0
M=D
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
// pop static 1
@SP
AM=M-1
D=M
@Class2.1
M=D
// push constant 0
@0
D=A
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
// function Class2.get 0
(Class2.get)
// push static 0
@Class2.0
D=M
@SP
A=M
M=D
@SP
M=M+1
// push static 1
@Class2.1
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
// ========== Sys.vm ==========
// function Sys.init 0
(Sys.init)
// push constant 6
@6
D=A
@SP
A=M
M=D
@SP
M=M+1
// push constant 8
@8
D=A
@SP
A=M
M=D
@SP
M=M+1
// call Class1.set 2
// push return address
@Class1.set$ret.1
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
// ARG = SP - 7
@SP
D=M
@7
D=D-A
@ARG
M=D
// LCL = SP
@SP
D=M
@LCL
M=D
// goto Class1.set
@Class1.set
0;JMP
(Class1.set$ret.1)
// pop temp 0
@SP
AM=M-1
D=M
@5
M=D
// push constant 23
@23
D=A
@SP
A=M
M=D
@SP
M=M+1
// push constant 15
@15
D=A
@SP
A=M
M=D
@SP
M=M+1
// call Class2.set 2
// push return address
@Class2.set$ret.2
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
// ARG = SP - 7
@SP
D=M
@7
D=D-A
@ARG
M=D
// LCL = SP
@SP
D=M
@LCL
M=D
// goto Class2.set
@Class2.set
0;JMP
(Class2.set$ret.2)
// pop temp 0
@SP
AM=M-1
D=M
@5
M=D
// call Class1.get 0
// push return address
@Class1.get$ret.3
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
// goto Class1.get
@Class1.get
0;JMP
(Class1.get$ret.3)
// call Class2.get 0
// push return address
@Class2.get$ret.4
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
// goto Class2.get
@Class2.get
0;JMP
(Class2.get$ret.4)
// label END
(Sys.init$END)
// goto END
@Sys.init$END
0;JMP
// ---------- end of program: halt ----------
(END_OF_PROGRAM)
@END_OF_PROGRAM
0;JMP
