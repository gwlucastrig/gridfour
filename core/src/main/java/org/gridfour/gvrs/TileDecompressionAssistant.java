/* --------------------------------------------------------------------
 *
 * The MIT License
 *
 * Copyright (C) 2022  Gary W. Lucas.

 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 * ---------------------------------------------------------------------
 */

 /*
 * -----------------------------------------------------------------------
 *
 * Revision History:
 * Date     Name         Description
 * ------   ---------    -------------------------------------------------
 * 06/2022  G. Lucas     Created
 *
 * Notes:
 *
 * -----------------------------------------------------------------------
 */
package org.gridfour.gvrs;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/**
 * Implements a background thread to expedite the decompression
 * of data.
 */
public class TileDecompressionAssistant implements Runnable {

  /**
   * Implements a private class that is used to run decompression operations
   * in a background thread.
   */
  private class DecompTask {

    final int tileIndex;
    final RasterTile tile;
    final byte[][] packing;
    boolean isFinished;

    DecompTask(RasterTile tile, byte[][] packing) {
      this.tileIndex = tile.tileIndex;
      this.tile = tile;
      this.packing = packing;
      this.isFinished = false;
    }

    void process() throws IOException {
      int k = 0;
      for (TileElement e : tile.elements) {
        e.decode(codecMaster, packing[k++]);
      }
    }
  }

  final CodecMaster codecMaster;
  private Thread taskThread;
  private boolean stopRequested = false;
  private final ArrayDeque<DecompTask> taskQueue = new ArrayDeque<>();
  private DecompTask taskInProgress = null;

  // statistics gathering ----------------------------
  int nInterruptedWaits;

  ArrayList<RasterTile> resultList = new ArrayList<>();

  TileDecompressionAssistant(GvrsFileSpecification specification) {
    codecMaster = new CodecMaster(specification.codecList);
  }

  private boolean isIndexPending(int target) {
    if (taskInProgress != null && taskInProgress.tileIndex == target) {
      return true;
    }
    for (DecompTask t : taskQueue) {
      if (t.tileIndex == target) {
        return true;
      }
    }
    return false;
  }

  void submitDecompression(RasterTile tile, byte[][] packing) {
    DecompTask task = new DecompTask(tile, packing);
    synchronized (this) {
      taskQueue.addLast(task);
      this.notifyAll();
    }
  }

  /**
   * Starts the internal task-processing thread.
   */
  void start() {
    synchronized (this) {
      if (taskThread == null) {
        taskThread = new Thread(this);
        taskThread.setName("GVRS Reading Assistant");
        taskThread.start();
      }
    }
  }

  @Override
  public void run() {
    while (true) {
      DecompTask task = null;
      // the inner loop happens inside a synchronized block.
      // It fetches the next DecompTask to be processed
      // and then breaks.  The processing is done outside the
      // synchronized block, so that associated threads can
      // obtain results or submit additional tasks while processing
      // is running.
      synchronized (this) {
        innerLoop:
        while (true) {
          if (stopRequested) {
            // return from this method will terminate the thread.
            return;
          }
          if (taskInProgress != null) {
            // transfer the content of the task to the
            // results list and clear the references to the task.
            resultList.add(taskInProgress.tile);
            taskInProgress = null;
            notifyAll();
          }

          if (taskQueue.isEmpty()) {
            try {
              wait();
            } catch (InterruptedException intex) {
              nInterruptedWaits++;
            }
          } else {
            taskInProgress = taskQueue.getFirst();
            taskQueue.removeFirst();
            task = taskInProgress;
            break;  // break inner loop
          }
        } // end of innter loop
      }

      if (task != null) {
        try {
          task.process();
        } catch (IOException ioex) {
          // TO DO:
          System.out.println("Need to address this " + ioex.getMessage());
        }
      }

    }  // end of outter loop
  }

  /**
   * Gets a list of decompressed tiles from the results list,
   * performing a wait operation if the specified target tile
   * is still being processed.
   * <p>
   * It is expected that when this method returns multiple
   * tiles, they will be added to the tile cache in the order
   * they are given in the list. Since the target tile is considered
   * the highest priority tile, it is added to the result list
   * <strong>last</strong>. Thus it will be the last tile added to
   * the tile cache and will be treated as the "most-recently accessed tile".
   *
   * @param targetIndex the target tile.
   * @return a valid, potentially empty list
   */
  List<RasterTile> getTilesWithWaitForIndex(int targetIndex) {
    List<RasterTile> list = new ArrayList<>();
    synchronized (this) {
      // if the target index is pending, the application
      // thread waits for the background thread to finish processing.
      // The background thread will call notifyAll() to break the
      // application thread out of its wait.
      while (isIndexPending(targetIndex)) {
        try {
          this.wait();
        } catch (InterruptedException intex) {
          nInterruptedWaits++;
        }
      }

      if (resultList.isEmpty()) {
        // return the empty list
        return list;
      }

      // Return the results from finished tasks.  If the target tile
      // is among them, append it to the list last.
      RasterTile targetTile = null;
      for (RasterTile t : resultList) {
        if (t.tileIndex == targetIndex) {
          targetTile = t;
        } else {
          list.add(t);
        }
      }
      if (targetTile != null) {
        list.add(targetTile);
      }
      resultList.clear();
    }
    return list;
  }

  /**
   * Gets the count of tasks currently in progress.
   *
   * @return a positive integer.
   */
  int getPendingTaskCount() {
    synchronized (this) {
      return (taskInProgress == null ? 0 : 1) + taskQueue.size();
    }
  }

  /**
   * Waits for all running and queued tasks to complete. When this method
   * is done, the thread will be in a quiescent state and the CodecMaster
   * instance can be accessed safely by other threads.
   * <p>
   * It is assumed that in most cases, decompression tasks can be completed
   * relative quickly, so this method should not result in a long wait.
   */
  void waitForCompletion() {
    synchronized (this) {
      // if the target index is pending, the application
      // thread waits for the background thread to finish processing.
      // The background thread will call notifyAll() to break the
      // application thread out of its wait.
      while (!(taskQueue.isEmpty() && taskInProgress == null)) {
        try {
          this.wait();
        } catch (InterruptedException inex) {
          this.nInterruptedWaits++;
        }
      }
    }
  }

  /**
   * Instructs the reading assistance to shutdown as soon as it can.
   * If a decompression operation is currently in progress, there may
   * be a delay until it finishes.
   */
  void shutdown() {
    synchronized (this) {
      stopRequested = true;
      notifyAll();
    }
  }
}
