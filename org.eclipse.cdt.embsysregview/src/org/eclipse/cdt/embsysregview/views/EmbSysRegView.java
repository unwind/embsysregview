// This file is part of EmbSysRegView.
//
// EmbSysRegView is free software: you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// EmbSysRegView is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
//  You should have received a copy of the GNU General Public License
//  along with Foobar.  If not, see <http://www.gnu.org/licenses/>.

package org.eclipse.cdt.embsysregview.views;

import java.io.IOException;
import java.net.URL;
import java.text.ParseException;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.miginfocom.swt.MigLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.swt.widgets.TreeItem;
import org.eclipse.ui.part.*;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.util.IPropertyChangeListener;
import org.eclipse.jface.util.PropertyChangeEvent;
import org.eclipse.jface.viewers.*;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.ui.*;
import org.eclipse.swt.SWT;
import org.eclipse.cdt.debug.internal.core.model.CDebugTarget;
import org.eclipse.cdt.embsysregview.Activator;
import org.eclipse.core.runtime.Platform;
import org.eclipse.debug.core.DebugEvent;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.IDebugEventSetListener;
import org.eclipse.debug.core.model.IDebugTarget;
import org.jdom.Attribute;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.input.SAXBuilder;
import org.osgi.framework.Bundle;
import org.eclipse.cdt.debug.core.cdi.ICDISession;
import org.eclipse.cdt.debug.core.cdi.model.ICDITarget;
import org.eclipse.cdt.debug.mi.core.MIException;
import org.eclipse.cdt.debug.mi.core.MIFormat;
import org.eclipse.cdt.debug.mi.core.MISession;
import org.eclipse.cdt.debug.mi.core.cdi.model.Target;
import org.eclipse.cdt.debug.mi.core.command.CommandFactory;
import org.eclipse.cdt.debug.mi.core.command.MIDataWriteMemory;
import org.eclipse.cdt.debug.mi.core.output.MIInfo;

/**
 * Generates a View, displaying Registers for an Embedded Device
 */

public class EmbSysRegView extends ViewPart implements IDebugEventSetListener {
	private TreeViewer viewer;
	private TreeParent invisibleRoot;
	Label infoLabel;
	private Action doubleClickAction;
	private Image selectedImage, unselectedImage, selectedFieldImage, unselectedFieldImage;
	private MISession miSession;
	private Target miTarget;

	/**
	 * This is the Content Provider that present the Static Model to the
	 * TreeViewer
	 */
	class ViewContentProvider implements IStructuredContentProvider,
			ITreeContentProvider {

		public ViewContentProvider() {
			initialize();
			
			Activator.getDefault().getPreferenceStore().addPropertyChangeListener(new IPropertyChangeListener(){

				@Override
				public void propertyChange(PropertyChangeEvent event) {
					initialize();
					viewer.setInput(invisibleRoot);
					viewer.refresh();
					updateInfoLabel();
				}});
		}
		
		public void inputChanged(Viewer v, Object oldInput, Object newInput) {
		}

		public void dispose() {
		}

		public Object[] getElements(Object parent) {
			if (parent.equals(getViewSite())) {
				if (invisibleRoot == null)
					initialize();
				return getChildren(invisibleRoot);
			}
			return getChildren(parent);
		}

		public Object getParent(Object child) {
			if (child instanceof TreeElement) {
				return ((TreeElement) child).getParent();
			}
			return null;
		}

		public Object[] getChildren(Object parent) {
			if (parent instanceof TreeParent) {
				return ((TreeParent) parent).getChildren();
			}
			return new Object[0];
		}

		public boolean hasChildren(Object parent) {
			if (parent instanceof TreeParent)
				return ((TreeParent) parent).hasChildren();
			return false;
		}

		@SuppressWarnings("unchecked")
		private TreeParent LoadXML() throws JDOMException, IOException,
				ParseException {
			TreeParent tempRoot = new TreeParent("", "");
				
			Bundle bundle = Platform.getBundle("org.eclipse.cdt.embsysregview");
			IPreferenceStore store = Activator.getDefault().getPreferenceStore();
			String store_architecture = store.getString("architecture");
			String store_vendor = store.getString("vendor");
			String store_chip = store.getString("chip");
			String store_board = store.getString("board");
			URL fileURL = bundle.getEntry("data/"	+ store_architecture+"/"+store_vendor+"/"+store_chip+".xml");
			if (fileURL == null)
				return tempRoot;
			
			SAXBuilder builder = new SAXBuilder();
			builder.setValidation(true);
			
			Document doc = builder.build(fileURL);
			Element model = doc.getRootElement();

			List<Element> grouplist = model.getChildren("group");

			for (Element group : grouplist) {
				
					// Mandatory attribute name
					Attribute attr_gname = group.getAttribute("name");
					String gname;
					if (attr_gname != null)
						gname = attr_gname.getValue();
					else
						throw new ParseException("group requires name", 1);

					// Optional attribute description
					Attribute attr_gdescription = group
							.getAttribute("description");
					String gdescription;
					if (attr_gname != null)
						gdescription = attr_gdescription.getValue();
					else
						gdescription = "";

					TreeGroup obj_group = new TreeGroup(gname, gdescription);
					tempRoot.addChild(obj_group);

					List<Element> registergrouplist = group.getChildren();
					for (Element registergroup : registergrouplist) {
						// Mandatory attribute name
						Attribute attr_rgname = registergroup
								.getAttribute("name");
						String rgname;
						if (attr_rgname != null)
							rgname = attr_rgname.getValue();
						else
							throw new ParseException(
									"registergroup requires name", 1);

						// Optional attribute description
						Attribute attr_rgdescription = registergroup
								.getAttribute("description");
						String rgdescription;
						if (attr_rgdescription != null)
							rgdescription = attr_rgdescription.getValue();
						else
							rgdescription = "";

						TreeRegisterGroup obj_registergroup = new TreeRegisterGroup(
								rgname, rgdescription);
						obj_group.addChild(obj_registergroup);

						List<Element> registerlist = registergroup
								.getChildren();
						for (Element register : registerlist) {
							// Mandatory attribute name
							Attribute attr_rname = register
									.getAttribute("name");
							String rname;
							if (attr_rgname != null)
								rname = attr_rname.getValue();
							else
								throw new ParseException(
										"register requires name", 1);

							// Optional attribute description
							Attribute attr_rdescription = register
									.getAttribute("description");
							String rdescription;
							if (attr_rdescription != null)
								rdescription = attr_rdescription.getValue();
							else
								rdescription = "";

							// Mandatory attribute address
							Attribute attr_roffsetaddress = register
									.getAttribute("address");
							long raddress;
							if (attr_roffsetaddress != null)
								raddress = Long.parseLong(attr_roffsetaddress
										.getValue().substring(2), 16);
							else
								throw new ParseException(
										"register requires address", 1);

							// Optional attribute resetvalue
							Attribute attr_rresetvalue = register
									.getAttribute("resetvalue");
							long rresetvalue;
							if (attr_rresetvalue != null)
								rresetvalue = Long.parseLong(attr_rresetvalue
										.getValue().substring(2), 16);
							else
								rresetvalue = 0x00000000;

							// Optional attribute access
							Attribute attr_raccess = register
									.getAttribute("access");
							String raccess;
							if (attr_raccess != null)
								raccess = attr_raccess.getValue();
							else
								raccess = "RO";

							TreeRegister obj_register = new TreeRegister(rname,
									rdescription, raddress, rresetvalue, raccess);
							obj_registergroup.addChild(obj_register);
							
							Element register_description = register.getChild("description");
							if(register_description!=null)
							{
								rdescription = register_description.getText();
								obj_register.setDescription(rdescription);
							}
							
							List<Element> fieldlist = register.getChildren("field");
							for (Element field : fieldlist) {
								// Optional attribute board_id
								Attribute attr_fboard_id = field
										.getAttribute("board_id");
								String fboard_id;
								if (attr_fboard_id != null)
									fboard_id = attr_fboard_id.getValue();
								else
									fboard_id = "";

								// only digg deeper if field is going to be
								// added
								
								if (fboard_id.equals("")
										|| fboard_id.equals(store_board)) {
									// Mandatory attribute name
									Attribute attr_fname = field
											.getAttribute("name");
									String fname;
									if (attr_fname != null)
										fname = attr_fname.getValue();
									else
										throw new ParseException(
												"field requires name", 1);

									// Optional attribute description
									Attribute attr_fdescription = field
											.getAttribute("description");
									String fdescription;
									if (attr_fdescription != null)
										fdescription = attr_fdescription
												.getValue();
									else
										fdescription = "";

									// Mandatory attribute bitoffset
									Attribute attr_fbitoffset = field
											.getAttribute("bitoffset");
									byte fbitoffset;
									if (attr_fbitoffset != null)
										fbitoffset = Byte
												.parseByte(attr_fbitoffset
														.getValue());
									else
										throw new ParseException(
												"field requires bitoffset", 1);

									// Mandatory attribute bitlength
									Attribute attr_fbitlength = field
											.getAttribute("bitlength");
									byte fbitlength;
									if (attr_fbitlength != null)
										fbitlength = Byte
												.parseByte(attr_fbitlength
														.getValue());
									else
										throw new ParseException(
												"field requires bitlength", 1);

									Element field_description = field.getChild("description");
									if(field_description!=null)
										fdescription = field_description.getText();
									
									HashMap<String, String> interpretations = new HashMap<String, String>();
									List<Element> interpretationlist = field
											.getChildren("interpretation");
									for (Element interpretation : interpretationlist) {
										// Mandatory attribute key
										Attribute attr_key = interpretation
												.getAttribute("key");
										String key;
										if (attr_key != null)
											key = attr_key.getValue();
										else
											throw new ParseException(
													"interpretation requires key",
													1);

										// Mandatory attribute text
										Attribute attr_text = interpretation
												.getAttribute("text");
										String text;
										if (attr_text != null)
											text = attr_text.getValue();
										else
											throw new ParseException(
													"interpretation requires text",
													1);

										interpretations.put(key, text);
									}

									TreeField obj_field = new TreeField(fname,
											fdescription, fbitoffset,
											fbitlength, interpretations);
									obj_register.addChild(obj_field);
								}
							}
						}
					}
				}
			

			return tempRoot;
		}

		private void initialize() {
			// initialize invisibleRoot with an empty TreeParent to show an empty Tree if LoadXML fails
			invisibleRoot = new TreeParent("", "");
			try {
				invisibleRoot = LoadXML();
				
			} catch (JDOMException e) { 
			} catch (IOException e) {
			} catch (ParseException e) {
			}
		}
	}

	/**
	 * The constructor.
	 */
	public EmbSysRegView() {
		
		IDebugTarget[] targets_ = DebugPlugin.getDefault().getLaunchManager().getDebugTargets();
		for (IDebugTarget target:targets_)
		{
			if(target instanceof CDebugTarget)
			{
				CDebugTarget debugTarget = (CDebugTarget) target;

				ICDISession cdiSession = debugTarget.getCDISession();
				ICDITarget[] targets = cdiSession.getTargets();
				miSession = null;

				ICDITarget cdiTarget = null;
				for (int i = 0; i < targets.length; i++) {
					ICDITarget cdiTargetCandidate = targets[i];
					if (cdiTargetCandidate instanceof Target) {
						cdiTarget = cdiTargetCandidate;
						break;
					}
				}
				if (cdiTarget != null) {
					miTarget = (Target) cdiTarget;
					miSession = miTarget.getMISession();
				}
			}
			if(miSession!=null)
				return;
		}
	}

	public String longtobinarystring(long wert, int bits) {
		StringBuilder text = new StringBuilder();

		for (int i = bits; i > 0; i--) {
			text.append(Math.abs(wert % 2));
			wert /= 2;
		}
		text.reverse();
		return text.toString();
	}
	
	public String longtoHexString(long wert, int bits) {
		
		int hexdigits=bits/4;
		if(bits%4!=0)
			hexdigits++;
		
		StringBuilder hex = new StringBuilder();
		hex.append("0x");
		String x = Long.toHexString(wert);
		int missingLen = hexdigits - x.length();
		for (int i = 0; i < missingLen; i++)
			hex.append('0');
		hex.append(x.toUpperCase());
		return hex.toString();
	}
	
	private void updateInfoLabel()
	{
		IPreferenceStore store = Activator.getDefault().getPreferenceStore();
		String store_architecture = store.getString("architecture");
		String store_vendor = store.getString("vendor");
		String store_chip = store.getString("chip");
		String store_board = store.getString("board");
		
		if(invisibleRoot == null || !invisibleRoot.hasChildren())
			infoLabel.setText("ERROR: Please select a chip using the preference page (c++/Debug/EmbSys Register View)");
		else
			if(store_board=="")
				infoLabel.setText("Arch: "+store_architecture+"  Vendor: "+store_vendor+"  Chip: "+store_chip);
			else
				infoLabel.setText("Arch: "+store_architecture+"  Vendor: "+store_vendor+"  Chip: "+store_chip+"  Board: "+store_board);
	}

	public void setRegister(long laddress, long lvalue)
	{
		CommandFactory factory = miSession.getCommandFactory();
		
			String value = "0x" + Long.toHexString(lvalue);
			String address = Long.toString(laddress);
			MIDataWriteMemory mw = factory.createMIDataWriteMemory(0,
				address, MIFormat.HEXADECIMAL, 4, value);
			try {
				miSession.postCommand(mw);
				MIInfo info = mw.getMIInfo();
				
				if (info == null) {
					// TODO: handle ERROR ???
				}
			} catch (MIException e) {

			}
	}
	
	/**
	 * This is a callback that creates the viewer and initialize it.
	 */
	public void createPartControl(final Composite parent) {
		Bundle bundle = Platform.getBundle("org.eclipse.cdt.embsysregview");
		URL fileURL = bundle.getEntry("icons/selected_register.png");
		URL fileURL2 = bundle.getEntry("icons/unselected_register.png");
		URL fileURL3 = bundle.getEntry("icons/selected_field.png");
		URL fileURL4 = bundle.getEntry("icons/unselected_field.png");
		try {
			selectedImage = new Image(parent.getDisplay(),fileURL.openStream());
			unselectedImage = new Image(parent.getDisplay(),fileURL2.openStream());
			selectedFieldImage = new Image(parent.getDisplay(),fileURL3.openStream());
			unselectedFieldImage = new Image(parent.getDisplay(),fileURL4.openStream());
		} catch (Exception e) {
			selectedImage = PlatformUI.getWorkbench().getSharedImages().getImage(ISharedImages.IMG_DEC_FIELD_ERROR);
			unselectedImage = PlatformUI.getWorkbench().getSharedImages().getImage(ISharedImages.IMG_DEC_FIELD_ERROR);
			selectedFieldImage = PlatformUI.getWorkbench().getSharedImages().getImage(ISharedImages.IMG_DEC_FIELD_ERROR);
			unselectedFieldImage = PlatformUI.getWorkbench().getSharedImages().getImage(ISharedImages.IMG_DEC_FIELD_ERROR);
		}

		TreeViewerColumn column;

		parent.setLayout(new MigLayout("fill","",""));
		infoLabel = new Label(parent,SWT.NONE);
		infoLabel.setLayoutData("dock north,height 15px,width 100%,wmin 0");

		viewer = new TreeViewer(parent, SWT.H_SCROLL | SWT.V_SCROLL | SWT.FULL_SELECTION);
		viewer.getControl().setLayoutData("height 100%,width 100%,hmin 0,wmin 0");
		viewer.getTree().setLinesVisible(true);
		viewer.getTree().setHeaderVisible(true);
		
		
		// Registername
		column = new TreeViewerColumn(viewer, SWT.NONE);
		column.getColumn().setWidth(250);
		column.getColumn().setMoveable(false);
		column.getColumn().setText("Register");
		column.setLabelProvider(new ColumnLabelProvider() {
			
			@Override
			public Color getForeground(Object element) {
				if (element instanceof TreeRegister)
					if (((TreeRegister)element).isRetrievalActive())	
						return parent.getShell().getDisplay().getSystemColor(SWT.COLOR_DARK_GREEN);
				if (element instanceof TreeField)
					if (((TreeRegister)((TreeField)element).getParent()).isRetrievalActive())	
						return parent.getShell().getDisplay().getSystemColor(SWT.COLOR_DARK_GREEN);
				
				return null;
			}

			public String getText(Object element) {
				if (element instanceof TreeField)
					if (((TreeField) element).getBitLength() == 1)
						return element.toString()
								+ " (bit "
								+ String.valueOf(31 - ((TreeField) element)
										.getBitOffset()) + ")";
					else
						return element.toString()
								+ " (bits "
								+ String.valueOf(31 - ((TreeField) element)
										.getBitOffset())
								+ "-"
								+ String.valueOf(32 - (((TreeField) element)
										.getBitOffset() + ((TreeField) element)
										.getBitLength())) + ")";

				else
					return element.toString();
			}

			public Image getImage(Object obj) {
				if (obj instanceof TreeParent)
				{
					if (obj instanceof TreeRegister)
						if (((TreeRegister)obj).isRetrievalActive())
							return selectedImage;
						else
							return unselectedImage;					
				}
				if (obj instanceof TreeField)
				{
					if (((TreeRegister)((TreeField)obj).getParent()).isRetrievalActive())
						return selectedFieldImage;
					else
						return unselectedFieldImage;
				}
				return PlatformUI.getWorkbench().getSharedImages().getImage(ISharedImages.IMG_OBJ_FOLDER);
			}

		});

		// Hex Value
		column = new TreeViewerColumn(viewer, SWT.NONE);
		column.getColumn().setWidth(80);
		column.getColumn().setMoveable(false);
		column.getColumn().setText("Hex");
		column.setLabelProvider(new ColumnLabelProvider() {
			@Override
			public Color getForeground(Object element) {
				if (element instanceof TreeRegister)
					if (!((TreeRegister) element).isWriteOnly())
						if (((TreeRegister)element).hasValueChanged())	
							return parent.getShell().getDisplay().getSystemColor(SWT.COLOR_RED);
				if (element instanceof TreeField)
					if (!((TreeRegister) ((TreeField)element).getParent()).isWriteOnly())
						if (((TreeField)element).hasValueChanged())	
							return parent.getShell().getDisplay().getSystemColor(SWT.COLOR_RED);
				return null;
			}
			
			public String getText(Object element) {
				if (element instanceof TreeGroup)
					return "";
				if (element instanceof TreeRegisterGroup)
					return "";
				if (element instanceof TreeRegister)
					if (((TreeRegister) element).getValue() == -1)
						return "";
					else
						if (((TreeRegister) element).isWriteOnly())
							return "- write only -";
						else
							return longtoHexString(((TreeRegister) element)
								.getValue(), 32);
				if (element instanceof TreeField)
					if (((TreeRegister) ((TreeField) element).getParent())
							.getValue() == -1)
						return "";
					else
						if (((TreeRegister) ((TreeField)element).getParent()).isWriteOnly())
							return "- write only -";
						else
							return longtoHexString(((TreeField) element)
								.getValue(), ((TreeField) element)
								.getBitLength());
				else
					return element.toString();
			}

		});
		
		final TextCellEditor textCellEditor = new TextCellEditor(viewer.getTree());
		textCellEditor.setValidator(new ICellEditorValidator() {
		
			@Override
			public String isValid(Object value) {
				
				if(value instanceof String && ((String)value).startsWith("0x"))
				{
					String svalue=((String)value);
					long lvalue=Long.valueOf(svalue.substring(2, svalue.length()), 16);
					int bits=32;
					ISelection selection = viewer.getSelection();
					Object obj = ((IStructuredSelection)selection).getFirstElement();
					if(obj instanceof TreeField)
						bits=((TreeField)obj).getBitLength();
					long maxvalue=(1L<<bits)-1;
					if(lvalue >= 0 && lvalue <=maxvalue)
						return null;
					else
						return "out of range";					
				}	
				return null;
			}
		});
		
		column.setEditingSupport(new EditingSupport(viewer) {
			@Override
			protected boolean canEdit(Object element) {
				if (element instanceof TreeField && 
					((((TreeRegister)((TreeField)element).getParent()).isReadWrite()) || 
					 (((TreeRegister)((TreeField)element).getParent()).isWriteOnly())))
					return true;
				if (element instanceof TreeRegister && 
						(((TreeRegister)element).isReadWrite() || 
						 ((TreeRegister)element).isWriteOnly()))
					return true;
				return false;
			}

			@Override
			protected CellEditor getCellEditor(Object element) {
				return textCellEditor;
			}

			@Override
			protected Object getValue(Object element) {
				if (element instanceof TreeField && ((TreeField)element).getValue()!=-1)
					return longtoHexString(((TreeField) element).getValue(),((TreeField) element).getBitLength()); 
					
				if (element instanceof TreeRegister && ((TreeRegister)element).getValue()!=-1)
					return longtoHexString(((TreeRegister) element).getValue(), 32); 

				return null;
			}

			@Override
			protected void setValue(Object element, Object value) {
				if (element instanceof TreeRegister && ((String)value).startsWith("0x"))
				{
					String svalue=((String)value);
					long lvalue=Long.valueOf(svalue.substring(2, svalue.length()), 16);
					
					TreeRegister treeRegister = ((TreeRegister) element);
					if(treeRegister.getValue()!=-1 && treeRegister.getValue()!=lvalue)
					{
						long laddress = treeRegister.getRegisterAddress();
					
						// Update Value on device
						setRegister(laddress, lvalue);
					
						updateTreeFields(invisibleRoot); // Update all enabled Fields, not just the changed
						viewer.refresh();
					}	
				}
				
				if (element instanceof TreeField && ((String)value).startsWith("0x"))
				{
					String svalue=((String)value);
					long fvalue=Long.valueOf(svalue.substring(2, svalue.length()), 16);
					
					TreeField treeField = ((TreeField) element);
					if(treeField.getValue()!=-1 && treeField.getValue()!=fvalue)
					{
						TreeRegister treeRegister = ((TreeRegister)treeField.getParent());
						long laddress = treeRegister.getRegisterAddress();
					
						// calculate register value + modified field to write into register
						long rvalue=treeRegister.getValue();
						int bitLength = treeField.getBitLength();
						int bitOffset = treeField.getBitOffset();
						
						long mask = ~(((1L<<bitLength)-1)<<(32-bitOffset-bitLength));
						rvalue = rvalue & mask ; // clear field bits in register value
						mask = ~mask;	
						fvalue = fvalue<<(32-bitOffset-bitLength); // shift field value into its position in the register
						fvalue = fvalue & mask ; // just to be sure, cut everything but the field
						rvalue = rvalue | fvalue ; // blend the field value into the register value
						
						// Update Value in Target
						setRegister(laddress, rvalue);
					
						treeRegister.updateValue();
						viewer.refresh(treeRegister);
					}	
				}
				
				viewer.refresh(element);
			}
		}); 
		
		// Binary Value
		column = new TreeViewerColumn(viewer, SWT.NONE);
		column.getColumn().setWidth(200);
		column.getColumn().setMoveable(false);
		column.getColumn().setText("Bin");
		column.setLabelProvider(new ColumnLabelProvider() {
			@Override
			public Color getForeground(Object element) {
				if (element instanceof TreeRegister)
					if (!((TreeRegister) element).isWriteOnly())
						if (((TreeRegister)element).hasValueChanged())	
							return parent.getShell().getDisplay().getSystemColor(SWT.COLOR_RED);
				if (element instanceof TreeField)
					if (!((TreeRegister) ((TreeField)element).getParent()).isWriteOnly())
						if (((TreeField)element).hasValueChanged())	
							return parent.getShell().getDisplay().getSystemColor(SWT.COLOR_RED);
				
				return null;
			}
			
			public String getText(Object element) {
				if (element instanceof TreeGroup)
					return "";
				if (element instanceof TreeRegisterGroup)
					return "";
				if (element instanceof TreeRegister)
					if (((TreeRegister) element).getValue() == -1)
						return "";
					else
						if (((TreeRegister) element).isWriteOnly())
							return "------------- write only -------------";
						else
							return longtobinarystring(((TreeRegister) element)
								.getValue(), 32);
				if (element instanceof TreeField)
					if (((TreeRegister) ((TreeField) element).getParent())
							.getValue() == -1)
						return "";
					else
						if (((TreeRegister) ((TreeField)element).getParent()).isWriteOnly())
							return "------------- write only -------------";
						else
							return longtobinarystring(((TreeField) element)
								.getValue(), ((TreeField) element)
								.getBitLength());
				else
					return element.toString();
			}

		});
		
		// Register Reset Value (hex)
		column = new TreeViewerColumn(viewer, SWT.NONE);
		column.getColumn().setWidth(80);
		column.getColumn().setMoveable(false);
		column.getColumn().setText("Reset");
		column.setLabelProvider(new ColumnLabelProvider() {
			public String getText(Object element) {
				if (element instanceof TreeGroup)
					return "";
				if (element instanceof TreeRegisterGroup)
					return "";
				if (element instanceof TreeRegister)
					return longtoHexString(((TreeRegister) element).getResetValue()
								, 32);
				if (element instanceof TreeField)
					return "";
				else
					return "";
			}

		});

		// Register Access
		column = new TreeViewerColumn(viewer, SWT.NONE);
		column.getColumn().setAlignment(SWT.CENTER);
		column.getColumn().setWidth(50);
		column.getColumn().setMoveable(false);
		column.getColumn().setText("Access");
		column.setLabelProvider(new ColumnLabelProvider() {
			public String getText(Object element) {
				if (element instanceof TreeGroup)
					return "";
				if (element instanceof TreeRegisterGroup)
					return "";
				if (element instanceof TreeRegister)
					return ((TreeRegister) element).getType().toUpperCase();
				if (element instanceof TreeField)
					return "";
				else
					return "";
			}

		});

		
		// Register Address (hex)
		column = new TreeViewerColumn(viewer, SWT.NONE);
		column.getColumn().setWidth(80);
		column.getColumn().setMoveable(false);
		column.getColumn().setText("Address");
		column.setLabelProvider(new ColumnLabelProvider() {
			public String getText(Object element) {
				if (element instanceof TreeGroup)
					return "";
				if (element instanceof TreeRegisterGroup)
					return "";
				if (element instanceof TreeRegister)
					return longtoHexString(((TreeRegister) element).getRegisterAddress()
								, 32);
				if (element instanceof TreeField)
					return "";
				else
					return "";
			}

		});

		// Description
		column = new TreeViewerColumn(viewer, SWT.NONE);
		column.getColumn().setWidth(300);
		column.getColumn().setMoveable(false);
		column.getColumn().setText("Description");
		ColumnViewerToolTipSupport.enableFor(viewer);

		column.setLabelProvider(new CellLabelProvider() {

			public String getToolTipText(Object element) {
				if (element instanceof TreeRegister)
					return ((TreeRegister) element).getDescription();
				if (element instanceof TreeField)
					return ((TreeField) element).getDescription();
					
				return null;
			}

			public Point getToolTipShift(Object object) {
				return new Point(5,5);
			}

			public int getToolTipDisplayDelayTime(Object object) {
				return 200;
			}

			public int getToolTipTimeDisplayed(Object object) {
				return 0;
			}
			
			public void update(ViewerCell cell) {
				Object element = cell.getElement();
				if (element instanceof TreeGroup)
					cell.setText(((TreeGroup) element).getDescription());
				if (element instanceof TreeRegisterGroup)
					cell.setText(((TreeRegisterGroup) element).getDescription());
				if (element instanceof TreeRegister)
				{
					String fulltext = ((TreeRegister) element).getDescription();
					// Compile the pattern
				    String patternStr = "^(.*)$";
				    Pattern pattern = Pattern.compile(patternStr, Pattern.MULTILINE);
				    Matcher matcher = pattern.matcher(fulltext);
				    
				    if(matcher.find())
				    {
				    	String text = matcher.group(1);
				    	cell.setText(text);
				    }
				}
			    
				if (element instanceof TreeField)
					if (((TreeRegister) ((TreeField) element).getParent())
							.getValue() != -1)
						cell.setText(((TreeField) element).getInterpretation());
					else
						cell.setText("");
			}
		});
		
		doubleClickAction = new Action() {
			public void run() {
				ISelection selection = viewer.getSelection();
				
				Object obj = ((IStructuredSelection) selection)
						.getFirstElement();

				int selectedColumn = -1;

				Tree tree = viewer.getTree();
				Point pt = tree.toControl(Display.getCurrent()
						.getCursorLocation());
				viewer.getTree().getColumnCount();
				for (int i = 0; i < tree.getColumnCount(); i++) {
					TreeItem item = tree.getItem(pt);
					if (item != null) {
						if (item.getBounds(i).contains(pt)) {
							selectedColumn = i;
						}
					}
				}
				
				if (obj instanceof TreeRegisterGroup && selectedColumn == 0) {
					TreeRegisterGroup treeRegisterGroup = ((TreeRegisterGroup)obj);
					TreeElement[] treeElements = treeRegisterGroup.getChildren();
					for(TreeElement treeElement:treeElements){
						TreeRegister treeRegister = ((TreeRegister)treeElement);
						if(!treeRegister.isWriteOnly())
						{
							treeRegister.toggleRetrieval();
							treeRegister.updateValue();
						}
					}
					viewer.refresh(obj);
				}

				if (obj instanceof TreeRegister && selectedColumn == 0) {
					TreeRegister treeRegister = ((TreeRegister)obj);
					if(!treeRegister.isWriteOnly())
					{
						treeRegister.toggleRetrieval();
						treeRegister.updateValue();
						viewer.refresh(obj);
					}
				}
				if(obj instanceof TreeField  && selectedColumn == 0)
				{
					TreeField treeField = ((TreeField)obj);
					TreeRegister treeRegister = ((TreeRegister)treeField.getParent());
					if(!treeRegister.isWriteOnly())
					{
						treeRegister.toggleRetrieval();
						treeRegister.updateValue();
						viewer.refresh(treeField.getParent());
					}
				}
			}
		};
		
		viewer.addDoubleClickListener(new IDoubleClickListener() {
			public void doubleClick(DoubleClickEvent event) {
				doubleClickAction.run();
			}
		});

		viewer.setContentProvider(new ViewContentProvider());
		updateInfoLabel();
		viewer.setInput(invisibleRoot);

		DebugPlugin.getDefault().addDebugEventListener(this);
	}

	/**
	 * Passing the focus request to the viewer's control.
	 */
	public void setFocus() {
		viewer.getControl().setFocus();
	}

	/**
	 * Updates Data for all TreeFields
	 */
	private void updateTreeFields(TreeElement element) {
		if (element instanceof TreeRegister)
			((TreeRegister) element).updateValue();
		else if (element instanceof TreeParent
				&& ((TreeParent) element).hasChildren()) {
			TreeParent pelement = (TreeParent) element;
			for (TreeElement telement : pelement.getChildren())
				updateTreeFields(telement);
		}
	}

	/**
	 * Clears Data from all TreeFields
	 */
	private void clearTreeFields(TreeElement element) {
		if (element instanceof TreeRegister)
			((TreeRegister) element).clearValue();
		else if (element instanceof TreeParent
				&& ((TreeParent) element).hasChildren()) {
			TreeParent pelement = (TreeParent) element;
			for (TreeElement telement : pelement.getChildren())
				clearTreeFields(telement);
		}
	}

	/**
	 * Handle DebugEvents while an active Debug Session
	 */
	@Override
	public void handleDebugEvents(DebugEvent[] events) {
		for (DebugEvent event : events) {

			Object source = event.getSource();
			
			if (event.getKind() == DebugEvent.SUSPEND && source instanceof CDebugTarget) {
				System.out.println("DebugEvent " + event.toString());
				
				CDebugTarget debugTarget = (CDebugTarget) source;

				ICDISession cdiSession = debugTarget.getCDISession();
				ICDITarget[] targets = cdiSession.getTargets();
				miSession = null;

				ICDITarget cdiTarget = null;
				for (int i = 0; i < targets.length; i++) {
					ICDITarget cdiTargetCandidate = targets[i];
					if (cdiTargetCandidate instanceof Target) {
						cdiTarget = cdiTargetCandidate;
						break;
					}
				}
				if (cdiTarget != null) {
					miTarget = (Target) cdiTarget;
					miSession = miTarget.getMISession();
				}
				updateTreeFields(invisibleRoot);

				Display.getDefault().asyncExec(new Runnable() {
					public void run() {
						viewer.refresh();
					}
				});
			}
			if (event.getKind() == DebugEvent.TERMINATE) {
				clearTreeFields(invisibleRoot);

				Display.getDefault().asyncExec(new Runnable() {
					public void run() {
						viewer.refresh();
					}
				});
			}
		}
	}
}