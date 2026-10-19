/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.commons.ui;

import java.awt.Image;
import java.util.ArrayList;
import java.util.List;

import javax.swing.ImageIcon;

public final class AppImages
{
	private static final String[] ICONS =
	{
		"images/l2jmobius_16x16.png",
		"images/l2jmobius_32x32.png",
		"images/l2jmobius_64x64.png",
		"images/l2jmobius_128x128.png",
	};
	
	private AppImages()
	{
	}
	
	/**
	 * @return the window icons, smallest first
	 */
	public static List<Image> icons()
	{
		final List<Image> icons = new ArrayList<>();
		for (String icon : ICONS)
		{
			icons.add(new ImageIcon(AppImages.class.getResource(icon)).getImage());
		}
		
		return icons;
	}
}
