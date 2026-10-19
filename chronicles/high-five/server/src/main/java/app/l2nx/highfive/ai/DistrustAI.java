/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2013 L2jMobius
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR
 * IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package app.l2nx.highfive.ai;

import app.l2nx.highfive.entity.actor.Attackable;
import app.l2nx.highfive.entity.actor.Creature;
import app.l2nx.highfive.geoengine.GeoEngine;

/**
 * @author Naker
 */
public class DistrustAI extends AttackableAI
{
	private final Creature _forcedTarget;
	
	public DistrustAI(Attackable actor, Creature forcedTarget)
	{
		super(actor);
		_forcedTarget = forcedTarget;
	}
	
	@Override
	public void thinkAttack()
	{
		if ((_forcedTarget == null) || _forcedTarget.isDead())
		{
			_actor.setTarget(null);
			setIntentionActive();
			return;
		}
		
		_actor.setTarget(_forcedTarget);
		setIntentionAttack(_forcedTarget);
		
		final int range = _actor.getPhysicalAttackRange() + _forcedTarget.getTemplate().getCollisionRadius();
		if (_actor.calculateDistance3D(_forcedTarget) > range)
		{
			moveToPawn(_forcedTarget, range);
			return;
		}
		
		if (!GeoEngine.getInstance().canSeeTarget(_actor, _forcedTarget))
		{
			return;
		}
		
		_actor.doAttack(_forcedTarget);
	}
}
