package ru.megaplan.jira.plugins.workflow.pusher;

import com.atlassian.sal.api.pluginsettings.PluginSettings;
import com.atlassian.sal.api.pluginsettings.PluginSettingsFactory;

/**
 * Created with IntelliJ IDEA.
 * User: firfi
 * Date: 08.06.12
 * Time: 14:44
 * To change this template use File | Settings | File Templates.
 */
public class PusherSettings {
    private final PluginSettingsFactory pluginSettingsFactory;
    private static final String KEY = PusherSettings.class.getName();

    public PusherSettings(final PluginSettingsFactory pluginSettingsFactory)
    {
        this.pluginSettingsFactory = pluginSettingsFactory;
    }

    public void setValue(final String key, final String value)
    {
        final PluginSettings settings = pluginSettingsFactory.createSettingsForKey(KEY+key);
        settings.put(key, value);
    }

    public String getValue(final String key)
    {
        final PluginSettings settings = pluginSettingsFactory.createSettingsForKey(KEY+key);
        return (String) settings.get(key);
    }
}
