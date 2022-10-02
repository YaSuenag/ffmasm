/*
 * Copyright (C) 2022 Yasumasa Suenaga
 *
 * This file is part of ffmasm.
 *
 * ffmasm is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * ffmasm is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with ffmasm.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.yasuenag.ffmasm;


/**
 * Thrown when platform functions fail.
 *
 * @author Yasumasa Suenaga
 */
public class PlatformException extends Exception{

  private final int errcode;

  /**
   * {@inheritDoc}
   */
  public PlatformException(Throwable cause){
    super(cause);
    this.errcode = 0;
  }

  /**
   * Constructs a new exception with the specified detail message and error code from platform.
   * The cause is not initialized.
   */
  public PlatformException(String message, int errcode){
    super(message);
    this.errcode = errcode;
  }

  /**
   * Returns error code which relates to this exception.
   * 0 means OS did not return any error (e.g. errcode = 0) - it means some error happen
   * in foreign function call in Java.
   *
   * @return error code which relates to this exception.
   */
  public int getErrCode(){
    return errcode;
  }

}
