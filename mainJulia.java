import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Image;
import java.awt.geom.Point2D;
import java.awt.image.MemoryImageSource;
import java.util.concurrent.atomic.AtomicInteger;
import javax.imageio.ImageIO;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

public class mainJulia {

    private static final double MIN_A = -1.0;
    private static final double MAX_A = 1.0;
    private static final double MIN_B = -1.0;
    private static final double MAX_B = 1.0;
    private static final int MIN_SIZE = 1;
    private static final int MAX_SIZE = 16384;
    private static final int MIN_THREADS = 1;
    private static final int MAX_THREADS = Runtime.getRuntime().availableProcessors();

    private static final int MIN_MODEL = 1;
    private static final int MAX_MODEL = 6;

    //Cartesian values of the screen
    private static final double CENTER_X = 0.0;
    private static final double CENTER_Y = 0.0;
    private static final double WIDTH = 3.25;
    private static final double HEIGHT = 3.25;
    private static AtomicInteger sharedCounter = new AtomicInteger(0);

    //Maximum number of iterations before a number is declared in the Julia set
    public static final int MAX_ITERATIONS = 100;

    //Distance from beyond which a point is not in the set
    public static final double THRESHOLD = 2.0;

    //private static final int WRONG_NUMBER_OF_ARGS_ERROR = 1; //not needed
    //private static final int BAD_ARG_ERROR = 2;

    public static void main(String[] args) {
        //Make sure we have the right number of arguments
        if (args.length != 5) {
            printUsage("Must have 5 command line arguments.");
            System.exit(1);
        }

        //Parse and check the arguments.
        double a, b;
        int size, numberOfThreads, model;
        try {
            a = parseDouble(args[0], "a", MIN_A, MAX_A);
            b = parseDouble(args[1], "b", MIN_B, MAX_B);
            size = parseInt(args[2], "size", MIN_SIZE, MAX_SIZE);
            numberOfThreads = parseInt(args[3], "threads", MIN_THREADS, MAX_THREADS);
            model = parseInt(args[4], "model", MIN_MODEL, MAX_MODEL);
        } catch (NumberFormatException ex) {
            printUsage(ex.getMessage());
            System.exit(1);
            return;
        }

        //Print number of cores
        System.out.printf("Running with %d threads on %d CPU cores.", numberOfThreads, Runtime.getRuntime().availableProcessors());

        //Make space for the image
        int[] data = new int[size * size];

        //Start clock
        final Stopwatch watch = new Stopwatch();


        if (numberOfThreads == 1) {
            //Run single-threaded mode
            for (int row = 0; row < size; row++) {
                for (int column = 0; column < size; column++) {
                    final Point2D.Double cartesianPoint = convertScreenToCartesian(column, row, size, size);
                    data[row * size + column] = juliaColor(cartesianPoint.getX(), cartesianPoint.getY(), a, b);
                }
            }
        } else {
            //Make a threads for drawing. The values are passed to the
            //Constructor, but we could have made them global.
            final List<JuliaDrawingThread> juliaDrawingThreads = new LinkedList<>();
            for (int threadNumber = 0; threadNumber < numberOfThreads; threadNumber++) {
                JuliaDrawingThread thread = new JuliaDrawingThread(data, a, b, threadNumber, numberOfThreads, size, model);
                juliaDrawingThreads.add(thread);
                thread.start();
            }

            //Wait for the threads to be done
            for (JuliaDrawingThread t : juliaDrawingThreads) {
                try {
                    t.join();
                } catch (InterruptedException ex) {
                    System.err.println("Execution was Interrupted!");
                }
            }
        }
        //Stop the clock
        System.out.printf("Drawing took %f seconds\n", watch.elapsedTime());

        //Show the image
        saveImage(data, size);
    }

    //Print a given message and some basic usage information
    private static void printUsage(String errorMessage) {
        System.err.println(errorMessage);
        System.err.println("The program arguments are:");
        System.err.printf("\ta: the Julia set's a constant [%f, %f]\n", MIN_A, MAX_A);
        System.err.printf("\tb: the Julia set's b constant [%f, %f]\n", MIN_B, MAX_B);
        System.err.printf("\tsize: the height and width for the image [%d, %d]\n", MIN_SIZE, MAX_SIZE);
        System.err.printf("\tthreads: the number of threads to use [%d, %d]\n", MIN_THREADS, MAX_THREADS);
        System.err.printf("\tthread model: the thread model to use [%d, %d]\n", MIN_MODEL, MAX_MODEL);
    }

    //Parse the given string s as a double and check that it is within the given range. If not
    //Throw a NumberFormatException.
    private static double parseDouble(String s, String name, double min, double max) {
        final double result;
        try {
            result = Double.parseDouble(s);
        } catch (NumberFormatException ex) {
            throw new NumberFormatException(String.format("Value, %s, given for %s is not a number", s, name));
        }

        if (result < min || result > max) {
            throw new NumberFormatException(String.format("Value, %f, given for %s is not in the range [%f, %f]",
                    result, name, min, max));
        }

        return result;
    }

    //Parse the given string s as an int and check that it is within the given range. If not
    //Throw a NumberFormatException. Very similar to parseDouble but I did not think it was
    //worth refactoring.
    private static int parseInt(String s, String name, int min, int max) {
        final int result;
        try {
            result = Integer.parseInt(s);
        } catch (NumberFormatException ex) {
            throw new NumberFormatException(String.format("Value, %s, given for %s is not a number", s, name));
        }
        if (result < min || result > max) {
            throw new NumberFormatException(String.format("Value, %d, given for %s is not in the range [%d, %d]",
                    result, name, min, max));
        }
        return result;
    }

    private static void saveImage(int[] data, int size) {
        BufferedImage julia = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        julia.setRGB(0, 0, size, size, data, 0, size);
        try {
            ImageIO.write(julia, "png", new File("julia.png"));
        } catch (IOException e) {
            System.out.println("Something went wrong...");
        }
    }

    //Return the color a given Cartesian point should be colored. Black if it is
    //in the Julia Set. Some other color if it is not.
    private static int juliaColor(double x, double y, double a, double b) {
        float color = 0;
        int i = 0;
        double distance = distance(x, y);

        //While we have not left the bounds and there are still iterations to go.
        //Note the test also increments i.
        while (distance < THRESHOLD && i++ < MAX_ITERATIONS) {
            //Apply the Julia Map
            double nextX = x * x - y * y + a;
            y = 2.0 * x * y + b;
            x = nextX;
            //Update distance
            distance = distance(x, y);
            color += Math.exp(-distance);
        }
        //If we are still within the bounds the point is in the set
        if (distance < THRESHOLD) {
            return Color.black.getRGB();
        }
        //Otherwise convert the hue into a color object
        return Color.getHSBColor(0.5f + 10 * color / MAX_ITERATIONS, 1.0f, 1.0f).getRGB();
    }


    private static double distance(double x, double y) {
        return distance(x, y, 0, 0);
    }

    //Not needed
    private static double distance(double x1, double y1, double x2, double y2) {
        return Math.sqrt((x1 - x2) * (x1 - x2) + (y1 - y2) * (y1 - y2));
    }


    //Convert the given point (x, y) in graphics coordinates into Cartesian
    //coordinates. This is just a linear transformation.
    private static Point2D.Double convertScreenToCartesian(double x, double y, int screenWidth, int screenHeight) {
        return new Point2D.Double(WIDTH / screenWidth * x + CENTER_X - WIDTH / 2.0,
                -HEIGHT / screenHeight * y + CENTER_Y + HEIGHT / 2.0);
    }

    //A thread for drawing Julia sets, does what it says...
    private static class JuliaDrawingThread extends Thread {
        //Copies of the values used for drawing
        private final double a, b;
        private final int startingRow, numberOfThreads;
        private final int[] buffer;
        private final int size;
        private final int model;

        public JuliaDrawingThread(int[] buffer, double a, double b, int startingRow, int numberOfThreads, int size, int model) {
            super("Julia Drawing Thread: " + startingRow + "/" + numberOfThreads);
            this.buffer = buffer;
            this.a = a;
            this.b = b;
            this.startingRow = startingRow; //change name
            this.numberOfThreads = numberOfThreads;
            this.size = size;
            this.model = model;
        }

        //The drawing code
        @Override
        public void run() {
            if (model == 1) {
                //rowStride();
                //Iterate over rows assigned to each thread
                for (int row = startingRow; row < size; row += numberOfThreads) {
                    for (int column = 0; column < size; column++) {
                        final Point2D.Double cartesianPoint = convertScreenToCartesian(column, row, size, size);
                        buffer[row * size + column] = juliaColor(cartesianPoint.getX(), cartesianPoint.getY(), a, b);
                    }
                }
            } else if (model == 2) {
                //blockSride(); //2 is size of block (rows)
                //Iterates over blocks with a size of (2 rows) assigned to each thread
                for (int block = startingRow * 4; block < size; block += numberOfThreads * 4) {
                    //Prevents us from going out of bounds, checks block size is less then 2 & does not exceed total rows
                    for (int offset = 0; offset < 4 && (block + offset) < size; offset++) {
                        int row = block + offset;
                        for (int column = 0; column < size; column++) {
                            final Point2D.Double cartesianPoint = convertScreenToCartesian(column, row, size, size);
                            buffer[row * size + column] = juliaColor(cartesianPoint.getX(), cartesianPoint.getY(), a, b);
                        }

                    }
                }
            } else if (model == 3) {
                //pixelStride();
                //Iterates over pixels assigned to thread.
                for (int threadID = startingRow; threadID < size * size; threadID += numberOfThreads) {
                    int row = threadID / size;
                    int column = threadID % size;
                    final Point2D.Double cartesianPoint = convertScreenToCartesian(column, row, size, size);
                    buffer[row * size + column] = juliaColor(cartesianPoint.getX(), cartesianPoint.getY(), a, b);
                }
            } else if (model == 4) {
                //nextfreeRow();
                int row;
                //Used atomic integer because it was the fastest counter in hw2
                //While loop to iterate over rows
                while ((row = sharedCounter.incrementAndGet()) < size) {
                    for (int column = 0; column < size; column++) {
                        final Point2D.Double cartesianPoint = convertScreenToCartesian(column, row, size, size);
                        buffer[row * size + column] = juliaColor(cartesianPoint.getX(), cartesianPoint.getY(), a, b);
                    }
                }
            } else if (model == 5) {
                //nextfreePixel();
                int threadID;
                //Iterating over pixels
                while ((threadID = sharedCounter.incrementAndGet()) < size * size) {
                    int row = threadID / size;
                    int column = threadID % size;
                    final Point2D.Double cartesianPoint = convertScreenToCartesian(column, row, size, size);
                    buffer[row * size + column] = juliaColor(cartesianPoint.getX(), cartesianPoint.getY(), a, b);
                }
            } else if (model == 6) {
                int index;
                //nextfreeBlock();
                //Iterating over blocks
                while ((index = sharedCounter.getAndAdd(4)) < size) {
                    for (int offset = 0; offset < 4 && (index + offset) < size; offset++) {
                        int row = index + offset;
                        for (int column = 0; column < size; column++) {
                            final Point2D.Double cartesianPoint = convertScreenToCartesian(column, row, size, size);
                            buffer[row * size + column] = juliaColor(cartesianPoint.getX(), cartesianPoint.getY(), a, b);
                        }
                    }
                }
            }
        }
    }
}