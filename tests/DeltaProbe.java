import eu.kickmod.patcher.Delta;
import java.io.*;
import java.util.concurrent.atomic.AtomicBoolean;

public final class DeltaProbe {
    public static void main(String[] args) throws Exception {
        AtomicBoolean cancelled = new AtomicBoolean(args.length > 3 && args[3].equals("cancel"));
        try (InputStream input = new FileInputStream(args[1])) {
            Delta.apply(new File(args[0]), input, new File(args[2]), Long.parseLong(args.length > 4 ? args[4] : "0"), cancelled);
        }
    }
}
