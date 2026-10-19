JACK TETRIS
nand2tetris, project 9
==============================================================================

A complete game of Tetris for the Hack computer, written in Jack.


HOW TO RUN IT
-----------------------------------------------------------------------------
Open the VM emulator, load this directory, set the speed to "Fast" and run.

    tools/VMEmulator.sh

(The game is written against the standard Jack OS, so no .asm or .hack step is
needed. If the pieces fall too quickly or too slowly on your machine, the
constants at the top of TetrisGame.jack -- frameMs and the lock delay -- are
the ones to turn.)


CONTROLS
-----------------------------------------------------------------------------
    LEFT / RIGHT    move the piece sideways       (hold to repeat)
    DOWN            soft drop, 1 point per row    (hold to repeat)
    UP   or  Z      rotate clockwise / anticlockwise
    SPACE           hard drop, 2 points per row, locks at once
    C               park the piece in HOLD, or swap with what is there
    P               pause
    Q               quit
    ENTER           start, and play again from the game-over screen


WHAT IT DOES
-----------------------------------------------------------------------------
  *  A ten by twenty well, the seven standard tetrominoes, four rotations each.
  *  Wall kicks: a rotation that does not fit is retried a cell or two to the
     side, and finally one cell up, so pieces can still spin against a wall or
     in a narrow gap.
  *  A landing shadow showing where the falling piece will come to rest.
  *  NEXT and HOLD previews, each piece drawn centred in its box.
  *  A lock delay: a piece that lands gets a moment to be slid or spun before
     it sets, so a last-second save is possible. It can be renewed eight times,
     which stops a piece being kept alive forever by spinning it.
  *  Scoring: 100 / 300 / 500 / 800 for one to four rows, times the level, so
     one four-row clear beats four single rows by a factor of two.
  *  Fifteen levels. A level every ten rows; gravity goes from a step every 32
     frames to a step every 4.
  *  Completed rows flash before they vanish.
  *  Pieces are dealt from a shuffled bag of all seven kinds, so no kind is ever
     starved and none can appear more than twice in a row.


THE TWO INTERESTING PROBLEMS
-----------------------------------------------------------------------------
Telling the pieces apart.  The Hack screen is black and white, so the usual
seven colours are not available. Instead each kind gets its own fill pattern
inside its 12-pixel cell, and the patterns are chosen to mirror how the pieces
are related: S and Z get the same bars turned ninety degrees, because the
pieces are reflections of each other, and J and L get mirrored diagonals for
the same reason. I is solid, as the only straight piece; O is hollow, as the
only one that never rotates; T has a centre. See Blocks.jack.

Randomness with no clock.  The Hack machine has no clock, no timer and no
entropy of any kind, so there is nothing to seed a generator with. The title
screen counts how many times its blink loop runs before the player presses
ENTER and hands that over as the seed: human reaction time is the one
unpredictable quantity available. See Randomizer.jack, which also explains why
it throws away the low bits of its generator.


THE CLASSES
-----------------------------------------------------------------------------
    Main          set up, then play until the player stops
    Screens       the title and game-over screens
    TetrisGame    one game: the loop, the rules, the keyboard, the score
    Board         the well: which cells are full, and clearing lines
    Piece         the falling tetromino: moving, turning, wall kicks
    Shapes        the 28 rotations, packed one to an integer
    Blocks        pixels: board-to-screen coordinates, and the block patterns
    Hud           the panel beside the well
    Randomizer    which piece comes next, dealt from a shuffled bag
    Text          small helpers for writing on the character grid

Board holds the occupancy and Piece holds the geometry, so neither needs to
know how the other works: a piece asks the board whether four individual cells
are free, and that single question answers moving, turning and dropping alike.

Two details that the Jack language itself forces, and that the comments in the
source go into:

  *  Jack has no garbage collector, and evaluating a string constant allocates
     a String that is never freed. So every fixed label is built once at start
     up and kept, and anything printed repeatedly -- the score, the blinking
     prompt -- is padded with printChar rather than with a literal.

  *  Jack has a bitwise & but no shift, which is exactly enough to pack all 28
     piece rotations into 28 integers and unpack them with a table of powers of
     two built by doubling. The last entry, 2^15, overflows to -32768; the bit
     pattern is still right, so the overflow is the mechanism rather than a bug.
