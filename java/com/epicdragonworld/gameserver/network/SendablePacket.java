/*
 * This file is part of the Epic Dragon World project.
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package com.epicdragonworld.gameserver.network;

import java.io.ByteArrayOutputStream;

/**
 * @author Pantelis Andrianakis
 */
public class SendablePacket
{
	private final ByteArrayOutputStream _baos;
	
	public SendablePacket()
	{
		_baos = new ByteArrayOutputStream();
	}
	
	public void writeString(String value)
	{
		if (value != null)
		{
			try
			{
				final byte[] byteArray = value.getBytes("UTF-8");
				writeByte(byteArray.length);
				writeBytes(byteArray);
			}
			catch (Exception e)
			{
				_baos.write(0);
			}
		}
		else
		{
			_baos.write(0);
		}
	}
	
	public void writeBytes(byte[] array)
	{
		try
		{
			_baos.write(array);
		}
		catch (Exception e)
		{
		}
	}
	
	public void writeByte(int value)
	{
		_baos.write(value & 0xff);
	}
	
	public void writeShort(int value)
	{
		_baos.write(value & 0xff);
		_baos.write((value >> 8) & 0xff);
	}
	
	public void writeInt(int value)
	{
		_baos.write(value & 0xff);
		_baos.write((value >> 8) & 0xff);
		_baos.write((value >> 16) & 0xff);
		_baos.write((value >> 24) & 0xff);
	}
	
	public void writeLong(long value)
	{
		_baos.write((int) (value & 0xff));
		_baos.write((int) ((value >> 8) & 0xff));
		_baos.write((int) ((value >> 16) & 0xff));
		_baos.write((int) ((value >> 24) & 0xff));
		_baos.write((int) ((value >> 32) & 0xff));
		_baos.write((int) ((value >> 40) & 0xff));
		_baos.write((int) ((value >> 48) & 0xff));
		_baos.write((int) ((value >> 56) & 0xff));
	}
	
	public void writeFloat(float fvalue)
	{
		final int value = Float.floatToRawIntBits(fvalue);
		_baos.write(value & 0xff);
		_baos.write((value >> 8) & 0xff);
		_baos.write((value >> 16) & 0xff);
		_baos.write((value >> 24) & 0xff);
	}
	
	public void writeDouble(double dvalue)
	{
		final long value = Double.doubleToRawLongBits(dvalue);
		_baos.write((int) (value & 0xff));
		_baos.write((int) ((value >> 8) & 0xff));
		_baos.write((int) ((value >> 16) & 0xff));
		_baos.write((int) ((value >> 24) & 0xff));
		_baos.write((int) ((value >> 32) & 0xff));
		_baos.write((int) ((value >> 40) & 0xff));
		_baos.write((int) ((value >> 48) & 0xff));
		_baos.write((int) ((value >> 56) & 0xff));
	}
	
	public byte[] getSendableBytes()
	{
		// Encrypt bytes.
		final byte[] encryptedBytes = Encryption.encrypt(_baos.toByteArray());
		final int size = encryptedBytes.length;
		
		// Create two bytes for length (short - max length 32767).
		final byte[] lengthBytes = new byte[2];
		lengthBytes[0] = (byte) (size & 0xff);
		lengthBytes[1] = (byte) ((size >> 8) & 0xff);
		
		// Join bytes.
		byte[] result = new byte[size + 2];
		System.arraycopy(lengthBytes, 0, result, 0, 2);
		System.arraycopy(encryptedBytes, 0, result, 2, size);
		
		// Return the data.
		return result;
	}
}
