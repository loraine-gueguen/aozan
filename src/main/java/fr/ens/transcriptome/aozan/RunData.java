/*
 *                  Eoulsan development code
 *
 * This code may be freely distributed and modified under the
 * terms of the GNU Lesser General Public License version 2.1 or
 * later and CeCILL-C. This should be distributed with the code.
 * If you do not have a copy, see:
 *
 *      http://www.gnu.org/licenses/lgpl-2.1.txt
 *      http://www.cecill.info/licences/Licence_CeCILL-C_V1-en.txt
 *
 * Copyright for this code is held jointly by the Genomic platform
 * of the Institut de Biologie de l'École Normale Supérieure and
 * the individual authors. These should be listed in @author doc
 * comments.
 *
 * For more information on the Eoulsan project and its aims,
 * or to join the Eoulsan Google group, visit the home page
 * at:
 *
 *      http://www.transcriptome.ens.fr/eoulsan
 *
 */

package fr.ens.transcriptome.aozan;

import java.util.Collection;
import java.util.Map;

import com.google.common.base.Joiner;
import com.google.common.collect.Maps;

/**
 * This class define a RunData object that contains all information generated by
 * collectors.
 * @since 1.0
 * @author Laurent Jourdren
 */
public class RunData {

  private Map<String, String> map = Maps.newLinkedHashMap();

  //
  // Getters
  //

  /**
   * Get a key.
   * @param key key name
   * @return the value of the data for the key
   */
  public String get(final String key) {

    if (key == null)
      return null;

    return this.map.get(key.toLowerCase().trim());
  }

  /**
   * Get a boolean key.
   * @param key key name
   * @return the value of the data for the key
   */
  public boolean getBoolean(final String key) {

    return Boolean.parseBoolean(get(key));
  }

  /**
   * Get an integer key.
   * @param key key name
   * @return the value of the data for the key
   */
  public int getInt(final String key) {

    return Integer.parseInt(get(key));
  }

  /**
   * Get a long key.
   * @param key key name
   * @return the value of the data for the key
   */
  public long getLong(final String key) {

    return Long.parseLong(get(key));
  }

  /**
   * Get a float key.
   * @param key key name
   * @return the value of the data for the key
   */
  public float getFloat(final String key) {

    return Float.parseFloat(get(key));
  }

  /**
   * Get a double key.
   * @param key key name
   * @return the value of the data for the key
   */
  public double getDouble(final String key) {

    return Double.parseDouble(get(key));
  }

  //
  // Setters
  //

  /**
   * Set a key.
   * @param key key to set
   * @param value value of the key
   */
  public void put(final String key, final String value) {

    if (key == null)
      return;

    this.map.put(key.toLowerCase().trim(), value == null ? "" : value.trim());
  }

  /**
   * Set a key with a boolean value.
   * @param key key to set
   * @param value value of the key
   */
  public void put(final String key, final boolean boolValue) {

    put(key, Boolean.toString(boolValue));
  }

  /**
   * Set a key with an integer value.
   * @param key key to set
   * @param value value of the key
   */
  public void put(final String key, final int intValue) {

    put(key, Integer.toString(intValue));
  }

  /**
   * Set a key with a long value.
   * @param key key to set
   * @param value value of the key
   */
  public void put(final String key, final long longValue) {

    put(key, Long.toString(longValue));
  }

  /**
   * Set a key with a float value.
   * @param key key to set
   * @param value value of the key
   */
  public void put(final String key, final float floatValue) {

    put(key, Float.toString(floatValue));
  }

  /**
   * Set a key with a double value.
   * @param key key to set
   * @param value value of the key
   */
  public void put(final String key, final double doubleValue) {

    put(key, Double.toString(doubleValue));
  }

  /**
   * Set a key with an array of strings as value.
   * @param key key to set
   * @param value value of the key
   */
  public void put(final String key, final String... strings) {

    if (strings == null)
      put(key, (String) null);

    put(key, Joiner.on(',').join(strings));
  }

  /**
   * Set a key with a collection of strings as value.
   * @param key key to set
   * @param value value of the key
   */
  public void put(final String key, final Collection<String> strings) {

    if (strings == null)
      put(key, (String) null);

    put(key, Joiner.on(',').join(strings));
  }

  /**
   * Set the key and values of a RunData object in the current RunData.
   * @param data Data to add
   */
  public void put(final RunData data) {

    if (data == null)
      return;

    for (final Map.Entry<String, String> e : data.map.entrySet())
      this.map.put(e.getKey(), e.getValue());
  }

  //
  // Other methods
  //

  /**
   * Get the number of entries in RunData.
   * @return the number of entries
   */
  public int size() {

    return this.map.size();
  }

  @Override
  public String toString() {

    final StringBuilder sb = new StringBuilder();
    for (Map.Entry<String, String> e : this.map.entrySet()) {
      sb.append(e.getKey());
      sb.append('=');
      sb.append(e.getValue());
      sb.append('\n');
    }

    return sb.toString();
  }

  /**
   * Print the content of the object on standard output.
   */
  public void print() {

    for (Map.Entry<String, String> e : this.map.entrySet())
      System.out.println(e.getKey() + "=" + e.getValue());
  }

}
