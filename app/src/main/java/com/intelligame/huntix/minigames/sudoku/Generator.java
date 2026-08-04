/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2015 André Diermann
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 *
 * Fonte: https://github.com/a11n/sudoku (adattato al package Huntix)
 */
package com.intelligame.huntix.minigames.sudoku;

import java.util.Random;

/**
 * A Generator to generate random Sudoku {@link Grid} instances.
 */
public class Generator {
  private Solver solver;

  /**
   * Constructs a new Generator instance.
   */
  public Generator() {
    this.solver = new Solver();
  }

  /**
   * Generates a random {@link Grid} instance with the given number of empty {@link Grid.Cell}s.
   * <br><br>
   * Note: The complexity for a human player increases with an higher amount of empty {@link Grid.Cell}s.
   * @param numberOfEmptyCells the number of empty {@link Grid.Cell}s
   * @return a randomly filled Sudoku {@link Grid} with the given number of empty {@link Grid.Cell}s
   */
  public Grid generate(int numberOfEmptyCells) {
    Grid grid = generate();

    eraseCells(grid, numberOfEmptyCells);

    return grid;
  }

  private void eraseCells(Grid grid, int numberOfEmptyCells) {
    Random random = new Random();
    for (int i = 0; i < numberOfEmptyCells; i++) {
      int randomRow = random.nextInt(9);
      int randomColumn = random.nextInt(9);

      Grid.Cell cell = grid.getCell(randomRow, randomColumn);
      if (!cell.isEmpty()) {
        cell.setValue(0);
      } else {
        i--;
      }
    }
  }

  private Grid generate() {
    Grid grid = Grid.emptyGrid();

    solver.solve(grid);

    return grid;
  }
}
