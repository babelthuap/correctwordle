import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

class Lists {
  static List<String> readList(String filename) {
    var list = new ArrayList<String>();
    try {
      Scanner scanner = new Scanner(new File(filename));
      while (scanner.hasNextLine()) {
        String word = scanner.nextLine();
        list.add(word);
      }
      scanner.close();
    } catch (FileNotFoundException e) {
      System.out.println(e);
    }
    return list;
  }

  static <T> List<T> concat(List<T> a, List<T> b) {
    List<T> c = new ArrayList<T>(a.size() + b.size());
    c.addAll(a);
    c.addAll(b);
    return c;
  }

  private Lists() {}
}
