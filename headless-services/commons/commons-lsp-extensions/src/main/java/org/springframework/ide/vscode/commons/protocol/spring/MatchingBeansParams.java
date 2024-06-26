/*******************************************************************************
 * Copyright (c) 2023 VMware, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     VMware, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.commons.protocol.spring;

public class MatchingBeansParams extends BeansParams {
	
	private String beanType;

	public String getBeanTypeToMatch() {
		return beanType;
	}

	public void setBeanTypeToMatch(String beanType) {
		this.beanType = beanType;
	}

}
