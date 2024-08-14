'use strict';

import * as OS from "os";
import {
    commands,
    window,
    workspace,
    ExtensionContext,
    Uri,
    lm } from 'vscode';

import * as commons from '@pivotal-tools/commons-vscode';
import * as liveHoverUi from './live-hover-connect-ui';
import * as rewrite from './rewrite';

import { startDebugSupport } from './debug-config-provider';
import { ApiManager } from "./apiManager";
import { ExtensionAPI } from "./api";
import {registerClasspathService} from "@pivotal-tools/commons-vscode/lib/classpath";
import {registerJavaDataService} from "@pivotal-tools/commons-vscode/lib/java-data";
import * as setLogLevelUi from './set-log-levels-ui';
import { startTestJarSupport } from "./test-jar-launch";
import { startPropertiesConversionSupport } from "./convert-props-yaml";
import * as springBootAgent from './copilot/springBootAgent';
import { SpringCli } from './copilot/springCli';
import { applyLspEdit } from "./copilot/guideApply";
import { isLlmApiReady } from "./copilot/util";
import CopilotRequest, { logger } from "./copilot/copilotRequest";
import * as copilotResultsRenderer from "./copilot/copilotResultsRenderer";

const PROPERTIES_LANGUAGE_ID = "spring-boot-properties";
const YAML_LANGUAGE_ID = "spring-boot-properties-yaml";
const JAVA_LANGUAGE_ID = "java";
const XML_LANGUAGE_ID = "xml";
const FACTORIES_LANGUAGE_ID = "spring-factories";
const JPA_QUERY_PROPERTIES_LANGUAGE_ID = "jpa-query-properties";

const STOP_ASKING = "Stop Asking";

export const SPRINGCLI = new SpringCli();
/** Called when extension is activated */
export function activate(context: ExtensionContext): Thenable<ExtensionAPI> {

    // registerPipelineGenerator(context);
    let options : commons.ActivatorOptions = {
        DEBUG: false,
        CONNECT_TO_LS: true,
        extensionId: 'vscode-spring-boot',
        preferJdk: true,
        jvmHeap: '1024m',
        vmArgs: [
        ],
        checkjvm: (context: ExtensionContext, jvm: commons.JVM) => {
            let version = jvm.getMajorVersion();
            if (version < 17) {
                throw Error(`Spring Tools Language Server requires Java 17 or higher to be launched. Current Java version is ${version}`);
            }

            if (!jvm.isJdk()) {
                window.showWarningMessage(
                    'JAVA_HOME or PATH environment variable seems to point to a JRE. A JDK is required, hence Boot Hints are unavailable.',
                    STOP_ASKING).then(selection => {
                        if (selection === STOP_ASKING) {
                            options.workspaceOptions.update('checkJVM', false);
                        }
                    }
                );
            }
        },
        workspaceOptions: workspace.getConfiguration("spring-boot.ls"),
        clientOptions: {
            markdown: {
                isTrusted: true
            },
            uriConverters: {
                code2Protocol: (uri) => {
           		        			/*
                    * Workaround for docUri coming from vscode-languageclient on Windows
                    * 
                    * It comes in as "file:///c%3A/Users/ab/spring-petclinic/src/main/java/org/springframework/samples/petclinic/owner/PetRepository.java"
                    * 
                    * While symbols index would have this uri instead:
                    * - "file:///C:/Users/ab/spring-petclinic/src/main/java/org/springframework/samples/petclinic/owner/PetRepository.java"
                    * 
                    * i.e. lower vs upper case drive letter and escaped drive colon
                    */
                    if (OS.platform() === "win32" && uri.scheme === 'file') {
                        let uriStr = uri.toString();
                        let idx = 5; // skip through `file:
                        for (; idx < uriStr.length - 1 && uriStr.charAt(idx) === '/'; idx++) {}
                        if (idx < uriStr.length - 1) {
                            // replace c%3A with C: or c: with C:
                            const replaceEscapedColon = idx < uriStr.length - 4 && uriStr.substring(idx + 1, idx + 4) === '%3A';
                            uriStr = `${uriStr.substring(0, idx)}${uriStr.charAt(idx).toUpperCase()}${replaceEscapedColon ? ':' : ''}${uriStr.substring(idx + (replaceEscapedColon ? 4 : 1))}`
                        }
                        return uriStr;
                    }
                    return uri.toString();
                },
                protocol2Code: uri => Uri.parse(uri)
            },
            // See PT-158992999 as to why a scheme is added to the document selector
            // documentSelector: [ PROPERTIES_LANGUAGE_ID, YAML_LANGUAGE_ID, JAVA_LANGUAGE_ID ],
            documentSelector: [
                {
                    language: PROPERTIES_LANGUAGE_ID,
                    scheme: 'file'
                },
                {
                    language: YAML_LANGUAGE_ID,
                    scheme: 'file'
                },
                {
                    language: JAVA_LANGUAGE_ID,
                    scheme: 'file'
                },
                {
                    language: JAVA_LANGUAGE_ID,
                    scheme: 'jdt'
                },
                {
                    language: XML_LANGUAGE_ID,
                    scheme: 'file'
                },
                {
                    language: FACTORIES_LANGUAGE_ID,
                    scheme: 'file'
                },
                {
                    language: JPA_QUERY_PROPERTIES_LANGUAGE_ID,
                    pattern: "**/jpa-named-queries.properties"
                }
            ],
            synchronize: {
                configurationSection: ['boot-java', 'spring-boot', 'http']
            },
            initializationOptions: () => ({
                workspaceFolders: workspace.workspaceFolders ? workspace.workspaceFolders.map(f => f.uri.toString()) : null,
                // Do not enable JDT classpath listeners at the startup - classpath service would enable it later if needed based on the Java extension mode
                // Classpath service registration requires commands to be registered and Boot LS needs to register classpath 
                // listeners when client has callbacks for STS4 extension java related messages registered via JDT classpath and Data Service registration
                enableJdtClasspath: false
            })
        },
        highlightCodeLensSettingKey: 'boot-java.highlight-codelens.on'
    };

    // Register launch config contributior to java debug launch to be able to connect to JMX
    context.subscriptions.push(startDebugSupport());

    return commons.activate(options, context).then(client => {
        commands.registerCommand('vscode-spring-boot.ls.start', () => client.start().then(() => {
            // Boot LS is fully started
            registerClasspathService(client);
            registerJavaDataService(client);

            // Force classpath listener to be enabled. Boot LS can only be launched iff classpath is available and there Spring-Boot on the classpath somewhere.
            commands.executeCommand('sts.vscode-spring-boot.enableClasspathListening', true);

            // Register TestJars launch support
            context.subscriptions.push(startTestJarSupport());

        }));
        commands.registerCommand('vscode-spring-boot.ls.stop', () => client.stop());
        liveHoverUi.activate(client, options, context);
        rewrite.activate(client, options, context);
        setLogLevelUi.activate(client, options, context);
        startPropertiesConversionSupport(context);
        copilotResultsRenderer.activate(context);
        if(isLlmApiReady) {
            activateSpringBootParticipant(context);
            // spelExpressionsExplain(context);
        }
        else 
            window.showInformationMessage("Spring Boot chat participant is not available. Please use the vscode insiders version 1.90.0 or above and make sure all `lm` API is enabled.");

        registerMiscCommands(context);

        commands.registerCommand('vscode-spring-boot.agent.apply', applyLspEdit);

        return new ApiManager(client).api;
    });
}

function registerMiscCommands(context: ExtensionContext) {
    context.subscriptions.push(
        commands.registerCommand('vscode-spring-boot.spring.modulith.metadata.refresh', async () => {
            const modulithProjects = await commands.executeCommand('sts/modulith/projects');
            const projectNames = Object.keys(modulithProjects);
            if (projectNames.length === 0) {
                window.showErrorMessage('No Spring Modulith projects found');
            } else {
                const projectName = projectNames.length === 1 ? projectNames[0] : await window.showQuickPick(
                    projectNames,
                    { placeHolder: "Select the target project." },
                );
                commands.executeCommand('sts/modulith/metadata/refresh', modulithProjects[projectName]);
            }
        }),

        commands.registerCommand('vscode-spring-boot.open.url', (openUrl) => {
            const openWithExternalBrowser = workspace.getConfiguration("spring.tools").get("openWith") === "external";
            const browserCommand = openWithExternalBrowser ? "vscode.open" : "simpleBrowser.api.open";
            return commands.executeCommand(browserCommand, Uri.parse(openUrl));
        }),
    );
}

async function activateSpringBootParticipant(context: ExtensionContext) {
    const model = (await lm.selectChatModels(CopilotRequest.DEFAULT_MODEL_SELECTOR))?.[0];
    if (!model) {
        const models = await lm.selectChatModels();
        logger.error(`Not a suitable model. The available models are: [${models.map(m => m.name).join(', ')}]. Please make sure you have installed the latest "GitHub Copilot Chat" (v0.16.0 or later) and all \`lm\` API is enabled.`);
        return;
    }
    springBootAgent.activate(context);
}
// async function spelExpressionsExplain(context: ExtensionContext) {
//     commands.registerCommand('vscode-spring-boot.query.explain', async (userPrompt, range: Range) => {
//         console.log('spel.explain: ' + userPrompt);
//         window.showInformationMessage('spel.explain executed');
//         const systemPrompts: LanguageModelChatMessage[] = [
//             new LanguageModelChatMessage(LanguageModelChatMessageRole.User, "Your task is to explain the user query in detail."),
//             new LanguageModelChatMessage(LanguageModelChatMessageRole.User, "IMPORTANT: CONCLUDE YOUR RESPONSE WITH THE MARKER \"<|endofresponse|>\"  TO INDICATE END OF RESPONSE")
//         ];
//         const messages = [
//             LanguageModelChatMessage.User(userPrompt)
//         ];
//         const copilotRequest = new CopilotRequest(systemPrompts);
//         const response = await copilotRequest.chatRequest(messages, {}, null);
//         console.log(response);

//         copilotResultsProvider.updateResults([response]);
        
//         window.showInformationMessage('Response: '+ response);
//     })
// }

