/*******************************************************************************
 * Copyright (c) 2024 Broadcom
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Broadcom - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.java.annotations;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.ide.vscode.commons.java.IJavaProject;

public interface AnnotationAttributeCompletionProvider {
	
	default List<String> getCompletionCandidates(IJavaProject project) {
        return new ArrayList<>();
    }

	
	default Map<String, String> getCompletionCandidatesWithLabels(IJavaProject project) {
        return new HashMap<>();
    }

}
