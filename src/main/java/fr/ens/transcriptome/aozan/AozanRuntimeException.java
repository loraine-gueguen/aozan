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

/**
 * A nestable Aozan runtime exception. This class came from from Java Code. In
 * Eoulsan, checked exceptions are generally preferred to RuntimeExceptions, but
 * RuntimeExceptions can be used as a fall-back if you are implementing an
 * interface which doesn't support checked exceptions. If you do this, please
 * document this clearly in the implementing class.
 * @since 1.0
 * @author Laurent Jourdren
 * @author Matthew Pocock
 * @author Thomas Down
 */
public class AozanRuntimeException extends RuntimeException {

  //
  // Constructors
  //

  /**
   * Create a new AozanRuntimeException with a message.
   * @param message the message
   */
  public AozanRuntimeException(final String message) {
    // setMessage(message);
    super(message);
  }

  /**
   * Create a new AozanRuntimeException.
   */
  public AozanRuntimeException() {
    super();
  }

}
