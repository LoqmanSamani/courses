THE JACK OPERATING SYSTEM
nand2tetris, project 12
==============================================================================

Eight classes that stand between a Jack program and a machine that can only
add, negate and compare.

    Math        multiply, divide, sqrt, min, max, abs
    Memory      peek, poke, and the heap: alloc and deAlloc
    Array       new and dispose
    String      a fixed buffer plus a length, and conversion to and from int
    Output      text: a 96-glyph font drawn onto the screen bitmap
    Screen      graphics: pixels, lines, rectangles, circles
    Keyboard    the single memory-mapped key, and reading lines from it
    Sys         start-up, halt, wait, error


FOUR THINGS WORTH POINTING AT
-----------------------------------------------------------------------------
Memory addresses the whole machine as an array. peek and poke must reach an
arbitrary address and Jack has no pointers -- but an array variable is nothing
but a base address, so setting `ram = 0` makes `ram[i]` mean word i of the
machine. It is the one piece of type-cheating in the OS and it is what makes
the rest possible.

The free list is sorted by address so that blocks can be merged. Sorting costs
a walk on every deAlloc and buys the thing that matters: two free blocks that
are physically adjacent are also adjacent in the list. Without that merging, a
program that allocates and frees in a loop -- which is to say any program that
uses strings -- saws the heap into ever smaller pieces until a modest request
cannot be satisfied although most of the heap is free.

String.setInt works in negative numbers, which looks backwards and is
deliberate. The range of a 16-bit integer is not symmetric: -32768 has no
positive counterpart, so the usual "make it positive first" overflows on
exactly that input. Every non-negative number does have a negative counterpart,
so negating at the start instead of at the end makes the whole range work with
no special case.

Math.multiply writes its bit test out instead of calling a helper. That looks
worse and it matters enormously: a Jack function call costs around twenty-five
VM instructions, and a helper called sixteen times per multiplication -- with
every `*` in every program arriving here -- was most of this OS's running time
before it was inlined. Together with carrying the remainder through division
instead of multiplying it back, and precomputing the sixteen end-masks that
horizontal fills need, that took the OS from about eight times slower than the
reference implementation to about one and a tenth.


ON SPEED
-----------------------------------------------------------------------------
Screen fills whole words wherever it can. Setting one pixel costs a read, a
mask and a write; setting the sixteen that share a word costs the same if you
write the word directly. Every filled shape -- rectangles, circles, and the
horizontal runs inside them -- goes through one routine that writes whole words
in the middle of a run and masks only at the two ragged ends.

Output tracks the cursor's word address and which half of it the cursor sits
in, stepping both along as characters are printed, rather than dividing the
column by two on every character.

drawCircle uses the midpoint algorithm rather than a square root per row: a
square root costs eight multiplications, and the midpoint method pays none.
