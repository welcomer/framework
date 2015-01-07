/*  Copyright 2014 White Label Personal Clouds Pty Ltd
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License. 
 */
package me.welcomer.signpost;

import java.io.UnsupportedEncodingException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.SignatureException;

import oauth.signpost.OAuth;
import oauth.signpost.exception.OAuthMessageSignerException;
import oauth.signpost.http.HttpParameters;
import oauth.signpost.http.HttpRequest;
import oauth.signpost.signature.OAuthMessageSigner;
import oauth.signpost.signature.SignatureBaseString;

/**
 * 
 * @see https://code.google.com/p/oauth-signpost/issues/detail?id=55
 */
@SuppressWarnings("serial")
public class RsaSha1MessageSigner extends OAuthMessageSigner
{
	private PrivateKey privateKey;

	public RsaSha1MessageSigner(final PrivateKey privateKey)
	{
		this.privateKey = privateKey;
	}

	@Override
	public String getSignatureMethod()
	{
		return "RSA-SHA1";
	}

	@Override
	public String sign(final HttpRequest request, final HttpParameters requestParams)
			throws OAuthMessageSignerException
	{
		byte[] keyBytes;
		byte[] signedBytes;
		String sbs = new SignatureBaseString(request, requestParams).generate();

		try
		{
			keyBytes = sbs.getBytes(OAuth.ENCODING);
			signedBytes = this.signBytes(keyBytes);
		}
		catch (InvalidKeyException e)
		{
			throw new OAuthMessageSignerException(e);
		}
		catch (NoSuchAlgorithmException e)
		{
			throw new OAuthMessageSignerException(e);
		}
		catch (SignatureException e)
		{
			throw new OAuthMessageSignerException(e);
		}
		catch (UnsupportedEncodingException e)
		{
			throw new OAuthMessageSignerException(e);
		}

		return this.base64Encode(signedBytes).trim();
	}

	private byte[] signBytes(final byte[] keyBytes) throws NoSuchAlgorithmException, InvalidKeyException, SignatureException
	{
		Signature signer;
		signer = Signature.getInstance("SHA1withRSA");
		signer.initSign(this.privateKey);
		signer.update(keyBytes);
		return signer.sign();
	}
}
