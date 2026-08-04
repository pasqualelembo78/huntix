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

import java.util.Optional;
import java.util.Random;

/**
 * A Solver is capable of solving a given Sudoku {@link Grid}.
 */
public class Solver {
  private static final int EMPTY = 0;
  
  private final int[] values;

  /**
   * Constructs a new Solver instance.
   */
  public Solver() {
    this.values = generateRandomValues();
  }

  /**
   * Solves a given {@link Grid} using backtracking.
   *
   * @param grid the {@link Grid} to solve
   * @throws IllegalStateException in case the provided {@link Grid} is invalid.
   */
  public void solve(Grid grid) {
    boolean solvable = solve(grid, grid.getFirstEmptyCell());

    if (!solvable) {
      throw new IllegalStateException("The provided grid is not solvable.");
    }
  }

  private boolean solve(Grid grid, Optional<Grid.Cell> cell) {
    if (!cell.isPresent()) {
      return true;
    }

    for (int value : values) {
      if (grid.isValidValueForCell(cell.get(), value)) {
        cell.get().setValue(value);
        if (solve(grid, grid.getNextEmptyCellOf(cell.get()))) return true;
        cell.get().setValue(EMPTY);
      }
    }

    return false;
  }

  private int[] generateRandomValues() {
    int[] values = { EMPTY, 1, 2, 3, 4, 5, 6, 7, 8, 9 };

    Random random = new Random();
    for (int i = 0, j = random.nextInt(9), tmp = values[j]; i < values.length;
        i++, j = random.nextInt(9), tmp = values[j]) {
      if(i == j) continue;
      
      values[j] = values[i];
      values[i] = tmp;
    }

    return values;
  }
}
