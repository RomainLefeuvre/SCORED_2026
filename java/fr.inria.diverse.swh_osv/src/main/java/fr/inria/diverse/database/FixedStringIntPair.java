package fr.inria.diverse.database;

import java.util.Arrays;

public final class FixedStringIntPair {
  private final char[] key = new char[40];
  private final int value;

  public FixedStringIntPair(String s, int value) {
      s.getChars(0, 40, this.key, 0);
      this.value = value;
  }

  public char[] getKey() { return key; }
  public int getValue() { return value; }


  @Override
  public boolean equals(Object o) {
      if (!(o instanceof FixedStringIntPair)) return false;
      FixedStringIntPair other = (FixedStringIntPair) o;
      return value == other.value && Arrays.equals(key, other.key);
  }

  @Override
  public int hashCode() {
      return Arrays.hashCode(key) * 31 + value;
  }
}