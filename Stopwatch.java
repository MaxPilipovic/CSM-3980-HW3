public class Stopwatch
{
    private final long start;

    public Stopwatch()
    {
        start = System.nanoTime();
    }

    public double elapsedTime()
    {
        return (System.nanoTime()- start) / 1000000000.0;
    }

}