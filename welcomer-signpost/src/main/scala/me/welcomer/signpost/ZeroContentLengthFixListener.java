package me.welcomer.signpost;

import java.net.HttpURLConnection;

import oauth.signpost.OAuthProviderListener;
import oauth.signpost.http.HttpRequest;
import oauth.signpost.http.HttpResponse;

// @see https://gist.github.com/miiCard/6140770
// @see http://www.miicard.com/blog/201308/walkthrough-doing-miicard-oauth-exchange-java
// @see https://code.google.com/p/oauth-signpost/issues/detail?id=60

public class ZeroContentLengthFixListener implements OAuthProviderListener
{
	@Override
	public void prepareRequest(final HttpRequest request) throws Exception
	{
		HttpURLConnection connection = (HttpURLConnection) request.unwrap();
		connection.setFixedLengthStreamingMode(0);
		connection.setDoOutput(true);
	}

	@Override
	public void prepareSubmission(final HttpRequest request) throws Exception
	{

	}

	@Override
	public boolean onResponseReceived(final HttpRequest request, final HttpResponse response)
			throws Exception
	{
		return false;
	}
}
