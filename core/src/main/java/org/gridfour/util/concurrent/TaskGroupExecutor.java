/* --------------------------------------------------------------------
 *
 * The MIT License
 *
 * Copyright (C) 2022  Gary W. Lucas.
 *
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
 * 03/2022  G. Lucas     Created
 *
 * Notes:
 *
 * -----------------------------------------------------------------------
 */
package org.gridfour.util.concurrent;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Extends the Java ThreadPoolExecutor class to add support for
 * processing groups of tasks. The grouped tasks concept is
 * useful when an expensive operation
 * is parceled into separate tasks that can be run in parallel.
 * In such cases, applications often require a mechanism for recognizing
 * when all the subordinate tasks have completed.
 * <p>
 * This class is useful in special circumstances where a thread pool
 * is an appropriate solution for an application. Developers who
 * have requirements in this area may also consider the Java standard API
 * classes CyclicBarrier and Phaser.
 * <p>
 * <strong>Using this class</strong>
 * <p>
 * You may use this class just as if it were a regular ThreadPoolExecutor.
 * But if you wish to take advantage of the extended functionality, you may
 * do so by applying the following steps:
 * <ol>
 * <li>CallgroupClear() to clear out any lingering elements
 * from previous runs.</li>
 * <li>Rather than using the conventional execute() method to process
 * Runnable instances, pass them to the groupExecute() method.
 * This will register them for management as part of a task group.</li>
 * <li>Call the groupWaitUntilDone() method. This will monitor the executor
 * until all runnables in the group have completed their work. Once the
 * last runnable is finished, it will return control to the calling module.</li>
 * </ol>
 * <p>
 * The following code fragment gives an example use of the TaskGroupExecutor.
 * <pre>
 *  static class Tester implements Runnable {
 *    String name;
 *    int delay;
 *    Tester(String name, int delay) {
 *      this.name = name;
 *      this.delay = delay;
 *    }
 *
 *    &#64;Override
 *    public void run() {
 *      System.out.println("  Start " + name + " " + Thread.currentThread().getName());
 *      try {
 *        Thread.sleep(delay * 1000);
 *      } catch (InterruptedException ex) {
 *      }
 *      System.out.println("  End   " + name);
 *    }
 *  }
 *
 *  public static void main(String[] args) {
 *    System.out.println("Begin Test 1");
 *    TaskGroupExecutor exec = new TaskGroupExecutor(4);
 *    exec.groupExecute(new Tester("alpha  ", 13));
 *    exec.groupExecute(new Tester("beta   ", 5));
 *    exec.groupExecute(new Tester("gamma  ", 3));
 *    exec.groupExecute(new Tester("delta  ", 2));
 *    exec.groupWaitUntilDone();
 *    System.out.println("Completion of Test 1");
 *
 *    System.out.println("\nBegin Test 2");
 *    exec.groupClearData();
 *    exec.groupExecute(new Tester("epsilon", 2));
 *    exec.groupExecute(new Tester("zeta   ", 2));
 *    exec.groupExecute(new Tester("eta    ", 2));
 *    exec.groupExecute(new Tester("theta  ", 2));
 *    exec.groupWaitUntilDone();
 *    System.out.println("Completion of Test 2");
 *
 *    exec.shutdown();
 * }
 * </pre>
 * The output will appear as follows.
 * <pre>
 *  * Begin Test 1
 *   Start alpha   pool-1-thread-1
 *   Start delta   pool-1-thread-4
 *   Start gamma   pool-1-thread-3
 *   Start beta    pool-1-thread-2
 *   End   delta
 *   End   gamma
 *   End   beta
 *   End   alpha
 * Completion of Test 1
 *
 * Begin Test 2
 *   Start epsilon pool-1-thread-4
 *   Start zeta    pool-1-thread-3
 *   Start eta     pool-1-thread-2
 *   Start theta   pool-1-thread-1
 *   End   theta
 *   End   eta
 *   End   zeta
 *   End   epsilon
 * Completion of Test 2
 * </pre>
 *
 * <p>
 * <strong>Special treatment for afterExecute()</strong> This class depends
 * on the afterExecute method associated with the base ThreadPoolExecutor
 * class.  If you wish to override that method in your own implementation,
 * be sure to call super.afterExecute() are part of your code.
 *
 */
public class TaskGroupExecutor extends ThreadPoolExecutor {

  final List<Runnable> taskList = new ArrayList();
  boolean taskFailure;

  /**
   * Provides a simplified constructor which is customized for processing
   * task groups.  In this constructor, a core pool of threads is allocated
   * and made available for processing.
   * <p>
   * In practice, it is a good idea to call shutdown() on a ThreadPoolExecutor
   * when it is no longer required.  This constructor implements a 15-second
   * timeout period after which idle threads will be terminated.
   * If you would prefer to not use that functionality, feel free to
   * create TaskGroupExecutor instances using any of the constructors
   * from the parent ThreadPoolExecutor class which is part of the Java
   * standard API.
   * @param corePoolSize the number of threads to keep in the pool,
   * even if they are idle, unless allowCoreThreadTimeOut is set.
   */
  public TaskGroupExecutor(int corePoolSize) {
    super(corePoolSize, corePoolSize,
      15, TimeUnit.SECONDS,
      new LinkedBlockingQueue<Runnable>());
    applyTimeOut();
  }

  private void applyTimeOut() {
    allowCoreThreadTimeOut(true);
  }

  @Override
  protected void afterExecute(Runnable r, Throwable t) {
    super.afterExecute(r, t);
    synchronized (this) {
      if (taskList.remove(r)) {
        if (t != null) {
          taskFailure = true;
        }
        this.notifyAll();
      }
    }
  }

  /**
   * Executes a given task sometime in the future. Note that Java does
   * not guarantee that jobs are executed in the order in which they are
   * submitted.
   *
   * @param runnable the task to execute.
   */
  public void groupExecute(Runnable runnable) {
    synchronized (this) {
      taskList.add(runnable);
    }
    super.execute(runnable);
  }

  /**
   * Clears any lingering elements from previous groups. If the group
   * execution completed successfully, no such lingering elements should
   * exist. But it is good practice to call this method whenever beginning
   * to execute a group of tasks.
   */
  public void groupClear() {
    List<Runnable> oldTasks = new ArrayList<>();
    synchronized (this) {
      oldTasks.addAll(taskList);
      taskList.clear();
      taskFailure = false;
    }
    oldTasks.forEach((r) -> {
      remove(r);
    });
  }

  /**
   * Waits until all tasks in the group have finished processing, then
   * returns control to the calling module. When this method returns,
   * an application can be certain that all tasks are finished. However,
   * it does not carry information about the success of failure of those
   * tasks. Mechanisms for handling such information, if required, must
   * be implemented by application code.
   *
   * @return true if all tasks completed successfully;
   * false if any task failed on an unhandled exception.
   */
  public boolean groupWaitUntilDone() {
    synchronized (this) {
      while (!taskList.isEmpty()) {
        try {
          this.wait();
        } catch (InterruptedException iex) {
          // no action required
        }
      }
      return !taskFailure;
    }
  }
}
