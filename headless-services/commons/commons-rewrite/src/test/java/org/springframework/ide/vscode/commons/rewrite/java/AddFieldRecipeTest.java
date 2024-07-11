package org.springframework.ide.vscode.commons.rewrite.java;

import static org.openrewrite.java.Assertions.java;

import org.junit.jupiter.api.Test;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

public class AddFieldRecipeTest implements RewriteTest {
	
	@Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new AddFieldRecipe("com.example.demo.OwnerRepository"))
        .parser(JavaParser.fromJavaVersion()
        		.logCompilationWarningsAndErrors(true));
    }
	
	
	@Test
    void addCustomField() {
        rewriteRun(
        	spec -> spec.recipe(new AddFieldRecipe("com.example.demo.OwnerRepository")),
            java(
                """
                    package com.example.demo;
                    
                    import com.example.demo.OwnerRepository;
                    class FooBar {
            		    
            		    public void test() {
            		    

            		    }
                    
                    }
                """,
                """
                    package com.example.demo;
                    
                    import com.example.demo.OwnerRepository;
                    class FooBar {
                    
                        private final OwnerRepository ownerRepository;
            		    
            		    public void test() {
            		    
                		
            		    }
                    
                    }               
                """
              )
        );
    }
	
	@Test
    void addFieldToNestedClasses() {
        rewriteRun(
        	spec -> spec.recipe(new AddFieldRecipe("com.example.demo.Inner.OwnerRepository")),
            java(
                """
                    package com.example.demo;
                    
                    import com.example.demo.Inner.OwnerRepository;
                    class FooBar {
            		    
                    }
                """,
                """
                    package com.example.demo;
                    
                    import com.example.demo.Inner.OwnerRepository;
                    class FooBar {
                        private final Inner.OwnerRepository ownerRepository;
            		    
                    }
                """
              )
        );
    }
	
//	@Test
//    void addFieldAndImportToNestedClasses() {
//        rewriteRun(
//        	spec -> spec.recipe(new AddFieldRecipe("com.example.demo.Inner.OwnerRepository")),
//            java(
//                """
//                    package com.example.demo;
//                    
//                    class FooBar {
//            		    
//                    }
//                """,
//                """
//                    package com.example.demo;
//                    
//                    import com.example.demo.Inner.OwnerRepository;
//                    class FooBar {
//                        private final Inner.OwnerRepository ownerRepository;
//            		    
//                    }
//                """
//              )
//        );
//    }

}
