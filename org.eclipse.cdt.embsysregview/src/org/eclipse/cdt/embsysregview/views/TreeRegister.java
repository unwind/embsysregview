/*******************************************************************************
 * Copyright (c) 2015 EmbSysRegView
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     ravenclaw78 - initial API and implementation
 *******************************************************************************/
package org.eclipse.cdt.embsysregview.views;

import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.debug.ui.DebugUITools;

public class TreeRegister extends TreeParent{
	private long old_value;
	private long value=-1;
	private long resetValue;
	private String type;
	private long registerAddress;
	private boolean retrievalActive = false;
	private int size=4;
	
	public boolean isReadWrite()
	{
		return (getType().toUpperCase().equals("RW") || 
				getType().toUpperCase().equals("RW1") || 
				getType().toUpperCase().equals("RW1C") || 
				getType().toUpperCase().equals("RW1S") || 
				getType().toUpperCase().equals("RWH"));
	}
	
	public boolean isReadOnly()
	{
		return (getType().toUpperCase().equals("RO") || 
				getType().toUpperCase().equals("RC") ||
				getType().toUpperCase().equals("R"));
	}
	
	public boolean isWriteOnly()
	{
		return (getType().toUpperCase().equals("WO") || 
				getType().toUpperCase().equals("W") || 
				getType().toUpperCase().equals("W1C") || 
				getType().toUpperCase().equals("W1S") || 
				getType().toUpperCase().equals("W1"));
	}
	
	public boolean hasValueChanged()
	{
		return (old_value!=value);
	}
	
	public boolean isRetrievalActive() {
		return retrievalActive;
	}

	public void setRetrievalActive(boolean retrievalActive) {
		this.retrievalActive = retrievalActive;
	}

	public long getRegisterAddress() {
		return registerAddress;
	}

	public TreeRegister(String name, String description, long registerAddress, long resetValue, String type, int size ) {
		super(name, description);
		this.registerAddress=registerAddress;
		this.resetValue=resetValue;
		this.type=type;		
		this.size=size;
	}
	
	protected long getOldValue()
	{
		return old_value;
	}

	public long getValue() {
		if(isWriteOnly())
			return resetValue;
		else
			return value;
	}
	
	public long getResetValue() {
		return resetValue;
	}

	public String getType() {
		return type;
	}
	
	/**
	 * Sets the Register
	 */
	public void setValue(long lvalue)
	{
		this.value=lvalue;
	}
	
	/**
	 * Sets the Registers value and writes it to the device
	 */
	public void setAndWriteValue(long lvalue)
	{ 
		if(isWriteOnly())
			writeValue(lvalue);
		else
		{
			old_value=value;
			setValue(lvalue);
			writeValue();
		}
	}
	
	/**
	 * Write value to the device 
	 */
	private void writeValue()
	{
		EmbSysRegView.GDBi.writeMemory(getRegisterAddress(), getValue(), getByteSize());
	}
	
	/**
	 * Write value to the device 
	 * 
	 * (needed for WriteOnly Registers... they ever return 0x00000000 on getValue()) 
	 */
	private void writeValue(long value)
	{
		EmbSysRegView.GDBi.writeMemory(getRegisterAddress(), value, getByteSize()); 
	}
	
	/**
	 * Reads the Registers value from live data in the Debug Context
	 */
	public void readValue()
	{
		if(retrievalActive && !isWriteOnly())
		{
			old_value=value;
			TreeElement parent = this.getParent();
			if (parent instanceof TreeRegisterGroup) {
				IAdaptable context = DebugUITools.getDebugContext();
				if(context!=null)
				{
					value = EmbSysRegView.GDBi.readMemory(registerAddress, getByteSize());
				}
				else
					value=-1;
			}
			else
				registerAddress=-1; //TODO: why this ???
		}
		else
		{
			old_value=value;
			value=-1;
		}
	}
	
	public int getBitSize() 
	{
		return size*8;
	}
	
	public int getByteSize() 
	{
		return size;
	}

	public void clearValue()
	{
		value=-1;
	}

	public void toggleRetrieval() {
		retrievalActive = !retrievalActive;
	}
}
