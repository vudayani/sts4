import { commands, ExtensionContext, extensions, lm, LanguageModelChatSelector, window, workspace, LogOutputChannel, version } from "vscode";
import { SemVer } from "semver";

export const REQUIRED_EXTENSION = 'github.copilot-chat';
const DEFAULT_MODEL_SELECTOR: LanguageModelChatSelector = { vendor: 'copilot', family: 'gpt-4' };
export const logger: LogOutputChannel = window.createOutputChannel("Spring tools copilot", { log: true });

export async function activateCopilotFeatures(context: ExtensionContext): Promise<void> {
    if(!isLlmApiAvailable("1.90.0-insider")) { // lm API is available since 1.90.0-insider
        return;
    }

    workspace.onDidChangeConfiguration(event => {
        if (event.affectsConfiguration('boot-java.highlight-copilot-codelens.on')) {
            promptReloadWindow();
        }
    });

    logger.info("vscode.lm is ready.");
    await ensureExtensionInstalledAndActivated();
    await updateConfigurationBasedOnCopilotAccess();

    // Add listener to handle installation/uninstallation of the required extension
    extensions.onDidChange(async () => {
        await ensureExtensionInstalledAndActivated();
        await updateConfigurationBasedOnCopilotAccess();
    });

    explainQueryWithCopilot();

}

function isLlmApiAvailable(v: string): boolean {
    return new SemVer(version).compare(new SemVer(v)) >= 0;
}

async function ensureExtensionInstalledAndActivated() {
    if (!isExtensionInstalled(REQUIRED_EXTENSION)) {
        logger.error(`Required extension ${REQUIRED_EXTENSION} is not installed.`);
        return;
    }

    if (!isExtensionActivated(REQUIRED_EXTENSION)) {
        logger.error(`Required extension ${REQUIRED_EXTENSION} is not activated.`);
        await waitUntilExtensionActivated(REQUIRED_EXTENSION);
    }
}

function isExtensionInstalled(extensionId: string): boolean {
    return !!extensions.getExtension(extensionId);
}

function isExtensionActivated(extensionId: string): boolean {
    return !!extensions.getExtension(extensionId)?.isActive;
}

async function waitUntilExtensionActivated(extensionId: string, interval: number = 3500) {
    logger.info(`Waiting for extension ${extensionId} to be activated...`);
    return new Promise<void>((resolve) => {
        const id = setInterval(() => {
            if (extensions.getExtension(extensionId)?.isActive) {
                clearInterval(id);
                resolve();
            }
        }, interval);
    });
}

async function updateConfigurationBasedOnCopilotAccess() {

    if (!isExtensionInstalled(REQUIRED_EXTENSION) || !isExtensionActivated(REQUIRED_EXTENSION)) {
        await updateConfiguration(false);
        return;
    }

    const model = (await lm.selectChatModels(DEFAULT_MODEL_SELECTOR))?.[0];
    if (!model) {
        const models = await lm.selectChatModels();
        logger.error(`No suitable model, available models: [${models.map(m => m.name).join(', ')}]. Please make sure you have installed the latest "GitHub Copilot Chat" (v0.16.0 or later) and all \`lm\` API is enabled.`);
        await updateConfiguration(false);
    } else {
        await updateConfiguration(true);
    }
}

async function updateConfiguration(value: boolean) {
    const configValue = workspace.getConfiguration().get('boot-java.highlight-copilot-codelens.on');
    if(value && configValue === true) {
        commands.executeCommand('sts/enable/copilot/features', value);
    }
}

async function explainQueryWithCopilot() {
    commands.registerCommand('vscode-spring-boot.query.explain', async (userPrompt) => {
        await commands.executeCommand('workbench.action.chat.open', { query: userPrompt });
    })
}

async function promptReloadWindow() {
    const reload = await window.showInformationMessage(
        'Configuration updated. Please reload VS Code to apply changes.',
        'Reload'
    );

    if (reload === 'Reload') {
        await commands.executeCommand('workbench.action.reloadWindow');
    }
}